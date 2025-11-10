package com.airlinetracker.flightdata;

import com.airlinetracker.flightdata.dto.FlightData;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End Integration Test for Flight Data Service
 * 
 * Test Requirements (from ARCHITECTURE.md Flow 1):
 * 1. User sends GET /api/v1/flight/{ident}
 * 2. Service checks Redis cache
 * 3. Cache miss → Call FlightAware API
 * 4. Write to cache (TTL: 5 minutes)
 * 5. Publish event to Kafka
 * 6. Return data to user
 * 7. Second call uses cache (no API call)
 * 
 * Infrastructure:
 * - Redis (Testcontainers)
 * - Kafka (EmbeddedKafka)
 * - FlightAware API (WireMock)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(
    partitions = 1,
    topics = {"flight-data-updated"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9093",
    "eureka.client.enabled=false" // Disable Eureka for tests
})
class FlightDataIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static WireMockServer wireMockServer;

    // Testcontainers: Redis
    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // Kafka message queue for verification
    private static BlockingQueue<FlightData> kafkaMessages = new LinkedBlockingQueue<>();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis configuration
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);

        // FlightAware API configuration (WireMock)
        registry.add("flightaware.base-url", () -> "http://localhost:8089");
        registry.add("flightaware.api-key", () -> "test-api-key");
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    /**
     * Kafka listener to capture published messages
     */
    @KafkaListener(topics = "flight-data-updated", groupId = "test-group")
    public void consumeKafkaMessage(FlightData flightData) {
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
    void shouldCompleteFullFlightDataFlow() throws InterruptedException {
        // Arrange: Clear cache and Kafka queue
        String ident = "UAL123";
        String cacheKey = "flights::UAL123"; // Spring Cache default key format
        redisTemplate.delete(cacheKey);
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

        // Assert 2: Data is cached in Redis
        Object cachedData = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedData).isNotNull();

        // Assert 3: Event published to Kafka
        FlightData kafkaMessage = kafkaMessages.poll(5, TimeUnit.SECONDS);
        assertThat(kafkaMessage).isNotNull();
        assertThat(kafkaMessage.getIdent()).isEqualTo("UAL123");
        assertThat(kafkaMessage.getFaFlightId()).isEqualTo("UAL123-1678886400-airline-0123");

        // Assert 4: FlightAware API was called ONCE
        verify(1, getRequestedFor(urlEqualTo("/flights/" + ident)));

        // Act 2: Second request (should use cache, no API call)
        long startTime2 = System.currentTimeMillis();
        ResponseEntity<FlightData> response2 = restTemplate.getForEntity(
                "/api/v1/flight/" + ident,
                FlightData.class
        );
        long duration2 = System.currentTimeMillis() - startTime2;

        // Assert 5: Second response is identical
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isNotNull();
        assertThat(response2.getBody().getIdent()).isEqualTo("UAL123");

        // Assert 6: FlightAware API was NOT called again (still 1 call total)
        verify(1, getRequestedFor(urlEqualTo("/flights/" + ident)));

        // Assert 7: Second call is faster (cache hit < 500ms as per PRD)
        assertThat(duration2).isLessThan(500);

        System.out.println("✅ First request (cache miss): " + duration1 + "ms");
        System.out.println("✅ Second request (cache hit): " + duration2 + "ms");
        System.out.println("✅ Cache speedup: " + ((duration1 - duration2) * 100 / duration1) + "%");
    }

    /**
     * Test: Flight not found (404)
     */
    @Test
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
    }

    /**
     * Test: Server error (500)
     */
    @Test
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

