package com.airlinetracker.flightdata;

import com.airlinetracker.flightdata.dto.FlightData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End Integration Test for Flight Data Service (Embedded Version)
 * 
 * Uses:
 * - Embedded Redis (no Docker required)
 * - Embedded Kafka (Spring Kafka Test)
 * - WireMock (for FlightAware API)
 * 
 * Test Requirements (from ARCHITECTURE.md Flow 1):
 * 1. User sends GET /api/v1/flight/{ident}
 * 2. Service checks Redis cache
 * 3. Cache miss → Call FlightAware API
 * 4. Write to cache (TTL: 5 minutes)
 * 5. Publish event to Kafka
 * 6. Return data to user
 * 7. Second call uses cache (no API call)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
    partitions = 1,
    topics = {"flight-data-updated"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6370",
    "spring.kafka.bootstrap-servers=localhost:9093",
    "eureka.client.enabled=false",
    "flightaware.base-url=http://localhost:8089",
    "flightaware.api-key=test-api-key"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlightDataIntegrationTestEmbedded {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static WireMockServer wireMockServer;
    private static RedisServer redisServer;

    // Kafka message queue for verification
    private static BlockingQueue<FlightData> kafkaMessages = new LinkedBlockingQueue<>();

    @BeforeAll
    static void startServers() throws IOException {
        // Start embedded Redis on port 6370
        redisServer = new RedisServer(6370);
        redisServer.start();

        // Start WireMock
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopServers() throws IOException {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // Clear cache before each test
        try {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        } catch (Exception e) {
            // Ignore if connection not ready
        }
        kafkaMessages.clear();
    }

    /**
     * Kafka listener to capture published messages
     */
    @KafkaListener(topics = "flight-data-updated", groupId = "test-group")
    public void consumeKafkaMessage(FlightData flightData) {
        System.out.println("Received Kafka message: " + flightData.getIdent());
        kafkaMessages.add(flightData);
    }

    /**
     * Test: Complete Flow (ARCHITECTURE.md Flow 1)
     * 
     * Scenario:
     * 1. First request → Cache miss → Call FlightAware → Cache + Kafka → Return
     * 2. Second request → Cache hit → No API call → Return (fast)
     */
    @Test
    @Order(1)
    void shouldCompleteFullFlightDataFlow() throws InterruptedException {
        // Arrange
        String ident = "UAL123";
        kafkaMessages.clear();

        // Mock FlightAware API response
        String flightAwareResponse = """
                {
                    "fa_flight_id": "UAL123-1678886400-airline-0123",
                    "ident": "UAL123",
                    "status": "En-Route / In Flight",
                    "scheduled_out": "2023-03-15T12:00:00Z",
                    "actual_out": "2023-03-15T12:05:00Z",
                    "scheduled_in": "2023-03-15T18:30:00Z",
                    "actual_in": null,
                    "origin": "KORD",
                    "destination": "KLAX",
                    "aircraft_type": "B738",
                    "latitude": 39.8,
                    "longitude": -98.6,
                    "altitude": 35000,
                    "groundspeed": 450
                }
                """;

        stubFor(get(urlEqualTo("/flights/" + ident))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(flightAwareResponse)));

        // Act 1: First request (should call API)
        long startTime1 = System.currentTimeMillis();
        ResponseEntity<FlightData> response1 = restTemplate.getForEntity(
                "/api/v1/flight/" + ident,
                FlightData.class
        );
        long duration1 = System.currentTimeMillis() - startTime1;

        // Assert 1: Response is correct
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotNull();
        assertThat(response1.getBody().getIdent()).isEqualTo("UAL123");
        assertThat(response1.getBody().getStatus()).isEqualTo("En-Route / In Flight");
        assertThat(response1.getBody().getOrigin()).isEqualTo("KORD");
        assertThat(response1.getBody().getDestination()).isEqualTo("KLAX");
        assertThat(response1.getBody().getAircraftType()).isEqualTo("B738");
        assertThat(response1.getBody().getLatitude()).isEqualTo(39.8);
        assertThat(response1.getBody().getGroundspeed()).isEqualTo(450);

        // Assert 2: Event published to Kafka
        FlightData kafkaMessage = kafkaMessages.poll(10, TimeUnit.SECONDS);
        assertThat(kafkaMessage).isNotNull();
        assertThat(kafkaMessage.getIdent()).isEqualTo("UAL123");
        assertThat(kafkaMessage.getFaFlightId()).isEqualTo("UAL123-1678886400-airline-0123");

        // Assert 3: FlightAware API was called ONCE
        verify(1, getRequestedFor(urlEqualTo("/flights/" + ident)));

        // Wait a moment for cache to be written
        Thread.sleep(500);

        // Act 2: Second request (should use cache, no API call)
        long startTime2 = System.currentTimeMillis();
        ResponseEntity<FlightData> response2 = restTemplate.getForEntity(
                "/api/v1/flight/" + ident,
                FlightData.class
        );
        long duration2 = System.currentTimeMillis() - startTime2;

        // Assert 4: Second response is identical
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().getIdent()).isEqualTo("UAL123");
        assertThat(response2.getBody().getStatus()).isEqualTo("En-Route / In Flight");

        // Assert 5: FlightAware API was NOT called again (still 1 call total)
        verify(1, getRequestedFor(urlEqualTo("/flights/" + ident)));

        // Assert 6: Second call is faster (cache hit < 500ms as per PRD)
        assertThat(duration2).isLessThan(500);

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║  ✅ INTEGRATION TEST RESULTS                       ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println("✅ First request (cache miss):  " + duration1 + "ms");
        System.out.println("✅ Second request (cache hit):  " + duration2 + "ms");
        System.out.println("✅ Cache speedup:                " + ((duration1 - duration2) * 100 / duration1) + "%");
        System.out.println("✅ Kafka event published:        YES");
        System.out.println("✅ API calls (expected 1):       " + wireMockServer.countRequestsMatching(getRequestedFor(urlEqualTo("/flights/" + ident)).build()).getCount());
        System.out.println("════════════════════════════════════════════════════════\n");
    }

    /**
     * Test: Flight not found (404)
     */
    @Test
    @Order(2)
    void shouldReturn404_WhenFlightNotFound() {
        // Arrange
        String ident = "INVALID999";
        stubFor(get(urlEqualTo("/flights/" + ident))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"error\": \"Flight not found\"}")));

        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/flight/" + ident,
                String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Flight not found");
    }

    /**
     * Test: Server error (500)
     */
    @Test
    @Order(3)
    void shouldReturn500_WhenFlightAwareApiError() {
        // Arrange
        String ident = "ERROR123";
        stubFor(get(urlEqualTo("/flights/" + ident))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\": \"Internal server error\"}")));

        // Act
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/flight/" + ident,
                String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

