package com.airlinetracker.llmsummary;

import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.dto.FlightSummaryResponse;
import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.repository.FlightSummaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End Integration Test for Flow 2 (ARCHITECTURE.md)
 *
 * Tests the complete asynchronous flow:
 * 1. Kafka event published to "flight-data-events" topic
 * 2. FlightDataConsumer receives event
 * 3. SummaryService calls OpenAI API (mocked)
 * 4. Summary saved to PostgreSQL
 * 5. REST API retrieves summary
 *
 * Uses Testcontainers for:
 * - PostgreSQL (real database)
 *
 * Uses EmbeddedKafka for:
 * - Kafka (embedded broker)
 *
 * Uses WireMock for:
 * - OpenAI API mock
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false"
        }
)
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"flight-data-events"}
)
@Import(TestKafkaConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("End-to-End Integration Test - Flow 2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

    // ========== Testcontainers Setup (PostgreSQL only) ==========

    @Container
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Configure Spring properties dynamically from Testcontainers
     * Note: Kafka bootstrap servers are automatically configured by @EmbeddedKafka
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Kafka topic configuration
        registry.add("kafka.topics.flight-data-events", () -> "flight-data-events");

        // OpenAI configuration (will be mocked by WireMock)
        registry.add("openai.base-url", () -> "http://localhost:9999");
        registry.add("openai.api-key", () -> "test-key-123");
        registry.add("openai.model", () -> "gpt-3.5-turbo");
        registry.add("openai.max-tokens", () -> "150");
        registry.add("openai.temperature", () -> "0.7");
    }

    // ========== Spring Components ==========

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, FlightData> testKafkaTemplate;

    @Autowired
    private FlightSummaryRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== WireMock for OpenAI API ==========

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void setUpWireMock() {
        wireMockServer = new WireMockServer(9999);
        wireMockServer.start();
        configureFor("localhost", 9999);
    }

    @AfterAll
    static void tearDownWireMock() {
            wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        // Clean database before each test
        repository.deleteAll();

        // Reset WireMock stubs
        wireMockServer.resetAll();

        // Configure ObjectMapper for Java 8 date/time
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ========== End-to-End Test ==========

    @Test
    @Order(1)
    @DisplayName("E2E: Complete Flow 2 - Kafka Event → OpenAI → Database → REST API")
    void testCompleteFlow2_KafkaToRestApi() throws Exception {
        // ========== STEP 1: Prepare Test Data ==========

        FlightData testFlightData = FlightData.builder()
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .status("En-Route / In Flight")
                .scheduledOut(Instant.parse("2025-11-10T09:00:00Z"))
                .actualOut(Instant.parse("2025-11-10T10:00:00Z"))
                .scheduledIn(Instant.parse("2025-11-10T14:00:00Z"))
                .actualIn(null)
                .origin("KORD")
                .destination("KLAX")
                .aircraftType("B738")
                .latitude(39.8283)
                .longitude(-98.5795)
                .altitude(35000)
                .groundspeed(450)
                .build();

        String expectedSummary = "United Flight 123 (UAL123) is currently en route from Chicago (KORD) to Los Angeles (KLAX). " +
                "The flight departed at 10:00 AM and is expected to arrive on schedule.";

        // ========== STEP 2: Mock OpenAI API ==========

        String openAiResponse = """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1677652288,
                    "model": "gpt-3.5-turbo",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "%s"
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": 200,
                        "completion_tokens": 50,
                        "total_tokens": 250
                    }
                }
                """.formatted(expectedSummary);

        stubFor(post(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key-123"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(openAiResponse)));

        System.out.println("✓ Step 2: OpenAI API mocked");

        // ========== STEP 3: Publish FlightData to Kafka ==========

        String topic = "flight-data-events";
        String key = testFlightData.getFaFlightId();

        System.out.println("✓ Step 3: Publishing FlightData to Kafka topic: " + topic);
        testKafkaTemplate.send(topic, key, testFlightData).get(5, TimeUnit.SECONDS);
        System.out.println("✓ FlightData published successfully");

        // ========== STEP 4: Wait for Consumer to Process (CountDownLatch alternative) ==========

        System.out.println("✓ Step 4: Waiting for Kafka consumer to process event...");

        // Poll database for summary (async processing takes time)
        boolean summaryFound = false;
        int maxAttempts = 20; // 20 attempts * 500ms = 10 seconds max
        int attempt = 0;

        while (!summaryFound && attempt < maxAttempts) {
            Optional<FlightSummary> summary = repository.findByFaFlightId(testFlightData.getFaFlightId());
            if (summary.isPresent()) {
                summaryFound = true;
                System.out.println("✓ Summary found in database after " + (attempt * 500) + "ms");
            } else {
                Thread.sleep(500);
                attempt++;
            }
        }

        assertThat(summaryFound)
                .withFailMessage("Summary was not saved to database within 10 seconds")
                .isTrue();

        // ========== STEP 5: Verify Summary Saved to Database ==========

        System.out.println("✓ Step 5: Verifying summary in database...");

        Optional<FlightSummary> savedSummary = repository.findByFaFlightId(testFlightData.getFaFlightId());

        assertThat(savedSummary).isPresent();
        assertThat(savedSummary.get().getIdent()).isEqualTo("UAL123");
        assertThat(savedSummary.get().getFaFlightId()).isEqualTo("UAL123-1234567890-1-0");
        assertThat(savedSummary.get().getSummaryText()).isEqualTo(expectedSummary);
        assertThat(savedSummary.get().getGeneratedAt()).isNotNull();
        assertThat(savedSummary.get().getLastUpdatedAt()).isNotNull();

        System.out.println("✓ Database verification passed");

        // Verify OpenAI API was called
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer test-key-123")));
        System.out.println("✓ OpenAI API was called as expected");

        // ========== STEP 6: Call GET /api/v1/flight/{ident}/summary ==========

        System.out.println("✓ Step 6: Calling REST API to retrieve summary...");

        String url = "http://localhost:" + port + "/api/v1/flight/UAL123/summary";
        ResponseEntity<FlightSummaryResponse> response = restTemplate.getForEntity(
                url,
                FlightSummaryResponse.class);

        System.out.println("✓ REST API response status: " + response.getStatusCode());

        // ========== STEP 7: Verify Response Matches Expected Summary ==========

        System.out.println("✓ Step 7: Verifying REST API response...");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        FlightSummaryResponse summaryResponse = response.getBody();
        assertThat(summaryResponse.getIdent()).isEqualTo("UAL123");
        assertThat(summaryResponse.getFaFlightId()).isEqualTo("UAL123-1234567890-1-0");
        assertThat(summaryResponse.getSummaryText()).isEqualTo(expectedSummary);
        assertThat(summaryResponse.getGeneratedAt()).isNotNull();

        System.out.println("✓ REST API verification passed");
        System.out.println("\n========================================");
        System.out.println("✅ END-TO-END TEST PASSED SUCCESSFULLY!");
        System.out.println("========================================\n");
        System.out.println("Summary: " + summaryResponse.getSummaryText());
    }

    @Test
    @Order(2)
    @DisplayName("E2E: Update Existing Summary When Same fa_flight_id Received")
    void testUpdateExistingSummary() throws Exception {
        // ========== STEP 1: Create Initial Summary ==========

        FlightData initialFlightData = FlightData.builder()
                .ident("DL456")
                .faFlightId("DL456-9876543210-1-0")
                .status("Scheduled")
                .scheduledOut(Instant.parse("2025-11-10T08:00:00Z"))
                .actualOut(Instant.parse("2025-11-10T08:00:00Z"))
                .scheduledIn(Instant.parse("2025-11-10T12:00:00Z"))
                .actualIn(null)
                .origin("KJFK")
                .destination("KSFO")
                .aircraftType("A320")
                .build();

        String initialSummary = "Delta Flight 456 is en route from New York to San Francisco.";

        // Mock OpenAI for initial request
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createOpenAiResponse(initialSummary))));

        // Publish initial event
        testKafkaTemplate.send("flight-data-events", initialFlightData.getFaFlightId(), initialFlightData)
                .get(5, TimeUnit.SECONDS);

        // Wait for processing
        Thread.sleep(3000);

        Optional<FlightSummary> firstSummary = repository.findByFaFlightId("DL456-9876543210-1-0");
        assertThat(firstSummary).isPresent();
        Long firstSummaryId = firstSummary.get().getId();

        // ========== STEP 2: Update Summary with New Data ==========

        String updatedSummary = "Delta Flight 456 has landed in San Francisco at 11:30 AM.";

        // Mock OpenAI for updated request
        wireMockServer.resetAll();
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createOpenAiResponse(updatedSummary))));

        // Publish update event (same fa_flight_id)
        FlightData updatedFlightData = FlightData.builder()
                .ident(initialFlightData.getIdent())
                .faFlightId(initialFlightData.getFaFlightId())
                .status("Landed")
                .scheduledOut(initialFlightData.getScheduledOut())
                .actualOut(initialFlightData.getActualOut())
                .scheduledIn(initialFlightData.getScheduledIn())
                .actualIn(Instant.parse("2025-11-10T11:30:00Z"))  // Updated field
                .origin(initialFlightData.getOrigin())
                .destination(initialFlightData.getDestination())
                .aircraftType(initialFlightData.getAircraftType())
                .build();

        testKafkaTemplate.send("flight-data-events", updatedFlightData.getFaFlightId(), updatedFlightData)
                .get(5, TimeUnit.SECONDS);

        // Wait for processing
        Thread.sleep(3000);

        // ========== STEP 3: Verify Update (Same ID, New Summary) ==========

        Optional<FlightSummary> updatedSummaryRecord = repository.findByFaFlightId("DL456-9876543210-1-0");
        assertThat(updatedSummaryRecord).isPresent();
        assertThat(updatedSummaryRecord.get().getId()).isEqualTo(firstSummaryId); // Same ID
        assertThat(updatedSummaryRecord.get().getSummaryText()).isEqualTo(updatedSummary); // Updated text

        // Verify only one record exists
        assertThat(repository.count()).isEqualTo(1);

        System.out.println("✅ Update test passed: Summary updated instead of creating duplicate");
    }

    @Test
    @Order(3)
    @DisplayName("E2E: Handle OpenAI API Error Gracefully")
    void testOpenAiApiError() throws Exception {
        // ========== STEP 1: Mock OpenAI API Failure ==========

        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\": \"Internal server error\"}")));

        // ========== STEP 2: Publish FlightData ==========

        FlightData testFlightData = FlightData.builder()
                .ident("SW789")
                .faFlightId("SW789-1111111111-1-0")
                .status("Scheduled")
                .origin("KLAS")
                .destination("KPHX")
                .aircraftType("B738")
                .build();

        testKafkaTemplate.send("flight-data-events", testFlightData.getFaFlightId(), testFlightData)
                .get(5, TimeUnit.SECONDS);

        // ========== STEP 3: Wait and Verify No Summary Saved ==========

        Thread.sleep(3000);

        Optional<FlightSummary> summary = repository.findByFaFlightId("SW789-1111111111-1-0");
        assertThat(summary).isEmpty(); // Summary should NOT be saved when OpenAI fails

        // ========== STEP 4: Verify 404 from REST API ==========

        String url = "http://localhost:" + port + "/api/v1/flight/SW789/summary";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Flight summary not found");

        System.out.println("✅ Error handling test passed: No summary saved on OpenAI failure");
    }

    @Test
    @Order(4)
    @DisplayName("E2E: Verify REST API Returns 404 for Non-Existent Flight")
    void testRestApiNotFound() {
        String url = "http://localhost:" + port + "/api/v1/flight/UAL999/summary";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("error")).isEqualTo("Flight summary not found");

        System.out.println("✅ 404 test passed");
    }

    // ========== Helper Methods ==========

    /**
     * Create OpenAI API response JSON
     */
    private String createOpenAiResponse(String summaryText) {
        return """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1677652288,
                    "model": "gpt-3.5-turbo",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "%s"
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": 200,
                        "completion_tokens": 50,
                        "total_tokens": 250
                    }
                }
                """.formatted(summaryText);
    }
}
