package com.airlinetracker.e2e;

import com.airlinetracker.e2e.dto.FlightDataResponse;
import com.airlinetracker.e2e.dto.FlightSummaryResponse;
import com.airlinetracker.e2e.util.HealthCheckUtil;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.client.WireMockBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
// Testcontainers not needed - Docker Compose runs separately

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * End-to-End Integration Test for Airline Tracking System
 * Phase 5: E2E Integration Testing
 * 
 * Tests the complete flow:
 * API Gateway → FlightData Service → Redis Cache → Kafka → LLM Summary Service → PostgreSQL
 * 
 * Requires the Compose stack described in integration-tests/README.md to be running:
 * Run: docker-compose -f docker-compose.e2e.yml up -d
 * 
 * Tests against real infrastructure:
 * - Service Registry (Eureka)
 * - API Gateway
 * - FlightData Service
 * - LLM Summary Service
 * - PostgreSQL
 * - Redis
 * - Kafka + Zookeeper
 * - WireMock (for FlightAware & OpenAI APIs)
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E Flight Tracking Integration Test Suite")
class E2EFlightTrackingIntegrationTest {

    private static final String API_GATEWAY_BASE_URL = "http://localhost:8080";
    private static final String WIREMOCK_BASE_URL = "http://localhost:8089";
    private static final int WIREMOCK_PORT = 8089;

    private static RestTemplate restTemplate;
    private static com.github.tomakehurst.wiremock.client.WireMock wireMockClient;

    @BeforeAll
    static void setupAll() {
        log.info("=".repeat(80));
        log.info("PHASE 5: E2E INTEGRATION TEST SUITE - INITIALIZATION");
        log.info("=".repeat(80));

        // Initialize REST client
        restTemplate = new RestTemplate();

        // Configure WireMock client
        wireMockClient = new WireMock("localhost", WIREMOCK_PORT);
        WireMock.configureFor("localhost", WIREMOCK_PORT);

        // Wait for all services to be healthy
        log.info("Waiting for all services to become healthy...");
        boolean allHealthy = HealthCheckUtil.waitForAllServices(API_GATEWAY_BASE_URL);

        if (!allHealthy) {
            throw new RuntimeException("❌ Not all services became healthy - cannot proceed with tests");
        }

        log.info("✅ All services are healthy - ready to execute E2E tests");
        log.info("=".repeat(80));
    }

    @BeforeEach
    void setup() {
        // Reset WireMock stubs before each test
        wireMockClient.resetMappings();
        log.info("🔄 WireMock stubs reset");
    }

    @AfterEach
    void teardown() {
        log.info("✅ Test completed\n");
    }

    @AfterAll
    static void teardownAll() {
        log.info("=".repeat(80));
        log.info("E2E INTEGRATION TEST SUITE - COMPLETED");
        log.info("=".repeat(80));
    }

    // ===========================================
    // TEST 1: HAPPY PATH - COMPLETE FLOW
    // ===========================================

    @Test
    @Order(1)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Test 1: Should complete full flight tracking flow end-to-end")
    void shouldCompleteFullFlightTrackingFlowEndToEnd() throws Exception {
        log.info("📋 TEST 1: Happy Path - Complete Flow");
        log.info("-".repeat(80));

        // ARRANGE
        String testIdent = "UAL123";
        String faFlightId = "UAL123-1678886400-airline-0123";

        log.info("🎯 Testing flight: {}", testIdent);

        // Mock FlightAware API response (persistent - matches multiple requests)
        stubFor(get(urlPathEqualTo("/flights/" + testIdent))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createFlightDataJson(testIdent, faFlightId))));

        log.info("✅ FlightAware API mocked");

        // Mock OpenAI API response (persistent - matches multiple requests)
        stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createOpenAIResponseJson(testIdent))));

        log.info("✅ OpenAI API mocked");

        // ACT - Step 1: Request flight data via API Gateway
        log.info("🚀 Step 1: Requesting flight data via API Gateway...");
        FlightDataResponse candidate = findSingleCandidate(testIdent);
        assertThat(candidate.getFaFlightId()).isEqualTo(faFlightId);
        ResponseEntity<FlightDataResponse> flightDataResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent + "/instance/" + faFlightId,
                FlightDataResponse.class
        );

        // ASSERT - Step 1: Verify flight data returned
        log.info("🔍 Step 1: Verifying flight data response...");
        assertThat(flightDataResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(flightDataResponse.getBody()).isNotNull();
        assertThat(flightDataResponse.getBody().getIdent()).isEqualTo(testIdent);
        assertThat(flightDataResponse.getBody().getFaFlightId()).isEqualTo(faFlightId);
        assertThat(flightDataResponse.getBody().getOrigin()).isEqualTo("KORD");
        assertThat(flightDataResponse.getBody().getDestination()).isEqualTo("KLAX");
        assertThat(flightDataResponse.getBody().getStatus()).contains("En-Route");

        log.info("✅ Step 1 PASSED: Flight data retrieved successfully");

        // Queue summary generation after the selected stable flight ID is known.
        queueSelectedSummary(testIdent, faFlightId);
        log.info("🔍 Step 2: Kafka event published (verified via consumer processing)");

        // ASSERT - Step 3: Verify LLM summary generated and saved to DB
        log.info("⏳ Step 3: Waiting for LLM summary to be generated (up to 30s)...");
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    try {
                        ResponseEntity<FlightSummaryResponse> summaryResponse = restTemplate.getForEntity(
                                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent + "/summary/" + faFlightId,
                                FlightSummaryResponse.class
                        );

                        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(summaryResponse.getBody()).isNotNull();
                        assertThat(summaryResponse.getBody().getIdent()).isEqualTo(testIdent);
                        assertThat(summaryResponse.getBody().getSummaryText())
                                .isNotNull()
                                .contains("United Flight 123")
                                .contains("En Route");

                        log.info("✅ Step 3 PASSED: LLM summary generated and saved");
                    } catch (RestClientException e) {
                        log.debug("Summary not ready yet, retrying...");
                        throw e;
                    }
                });

        // ACT - Step 4: Request summary via API Gateway (second time for cache verification)
        log.info("🚀 Step 4: Requesting summary again to verify it's cached...");
        ResponseEntity<FlightSummaryResponse> summaryResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent + "/summary/" + faFlightId,
                FlightSummaryResponse.class
        );

        // ASSERT - Step 4: Verify summary returned instantly
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaryResponse.getBody()).isNotNull();
        assertThat(summaryResponse.getBody().getFaFlightId()).isEqualTo(faFlightId);
        assertThat(summaryResponse.getBody().getSummaryText()).contains("United Flight 123");

        log.info("✅ Step 4 PASSED: Summary retrieved from database");

        // VERIFY PERFORMANCE: Second request to flight data should be faster (from Redis cache)
        log.info("⏱️  Step 5: Performance check - Cache hit test...");
        long startTime = System.currentTimeMillis();
        ResponseEntity<FlightDataResponse> cachedResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent + "/instance/" + faFlightId,
                FlightDataResponse.class
        );
        long duration = System.currentTimeMillis() - startTime;

        assertThat(cachedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duration).as("Cache hit should be fast (< 1000ms)").isLessThan(1000);

        log.info("✅ Step 5 PASSED: Cache hit completed in {}ms", duration);

        // Verify FlightAware API was called only ONCE (subsequent requests served from cache)
        verify(exactly(1), getRequestedFor(urlPathMatching("/flights/" + testIdent)));
        log.info("✅ Verified: FlightAware API called only once (cache working)");

        log.info("🎉 TEST 1 COMPLETE: Full E2E flow successful!");
    }

    // ===========================================
    // TEST 2: CACHE HIT PERFORMANCE
    // ===========================================

    @Test
    @Order(2)
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Test 2: Should serve subsequent requests from cache under 500ms")
    void shouldServeSubsequentRequestsFromCacheUnder500ms() throws Exception {
        log.info("📋 TEST 2: Cache Hit Performance");
        log.info("-".repeat(80));

        // ARRANGE
        String testIdent = "DAL456";
        String faFlightId = "DAL456-1678886401-airline-0456";

        log.info("🎯 Testing cache performance for flight: {}", testIdent);

        // Mock FlightAware API (persistent - matches multiple requests)
        stubFor(get(urlPathEqualTo("/flights/" + testIdent))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createFlightDataJson(testIdent, faFlightId))));

        // ACT - First request (cache miss)
        log.info("🚀 Making first request (cache miss)...");
        long firstRequestStart = System.currentTimeMillis();
        ResponseEntity<FlightDataResponse> firstResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent,
                FlightDataResponse.class
        );
        long firstRequestDuration = System.currentTimeMillis() - firstRequestStart;

        // ASSERT - First request successful
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        log.info("✅ First request completed in {}ms (cache miss)", firstRequestDuration);

        // Wait a moment for cache to propagate
        Thread.sleep(500);

        // ACT - Second request (cache hit)
        log.info("🚀 Making second request (cache hit)...");
        long secondRequestStart = System.currentTimeMillis();
        ResponseEntity<FlightDataResponse> secondResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent,
                FlightDataResponse.class
        );
        long secondRequestDuration = System.currentTimeMillis() - secondRequestStart;

        // ASSERT - Second request faster and from cache
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().getIdent()).isEqualTo(testIdent);

        // Performance assertion
        assertThat(secondRequestDuration)
                .as("Cache hit should be under 500ms")
                .isLessThan(500);

        log.info("✅ Second request completed in {}ms (cache hit)", secondRequestDuration);
        log.info("📊 Performance improvement: {}ms → {}ms", firstRequestDuration, secondRequestDuration);

        // Verify FlightAware API was called only ONCE
        verify(exactly(1), getRequestedFor(urlPathMatching("/flights/" + testIdent)));
        log.info("✅ Verified: FlightAware API called only once");

        log.info("🎉 TEST 2 COMPLETE: Cache performance verified!");
    }

    // ===========================================
    // TEST 3: ERROR HANDLING - FLIGHT NOT FOUND
    // ===========================================

    @Test
    @Order(3)
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Test 3: Should return 404 when flight not found")
    void shouldReturn404WhenFlightNotFound() {
        log.info("📋 TEST 3: Error Handling - Flight Not Found");
        log.info("-".repeat(80));

        // ARRANGE
        String testIdent = "INVALID999";

        log.info("🎯 Testing error handling for invalid flight: {}", testIdent);

        // Mock FlightAware API to return 404
        stubFor(get(urlPathEqualTo("/flights/" + testIdent))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Flight not found\"}")));

        log.info("✅ FlightAware API mocked to return 404");

        // ACT & ASSERT
        log.info("🚀 Requesting invalid flight...");

        assertThatThrownBy(() ->
                restTemplate.getForEntity(
                        API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent,
                        FlightDataResponse.class
                )
        )
                .isInstanceOfAny(HttpClientErrorException.NotFound.class, HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    log.info("✅ Received expected 404 Not Found");
                });

        log.info("🎉 TEST 3 COMPLETE: 404 error handling verified!");
    }

    // ===========================================
    // TEST 4: ERROR HANDLING - FLIGHTAWARE API DOWN
    // ===========================================

    @Test
    @Order(4)
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Test 4: Should handle FlightAware API failure gracefully")
    void shouldHandleFlightAwareAPIFailureGracefully() {
        log.info("📋 TEST 4: Error Handling - FlightAware API Down");
        log.info("-".repeat(80));

        // ARRANGE
        String testIdent = "UAL789";

        log.info("🎯 Testing error handling for FlightAware API failure: {}", testIdent);

        // Mock FlightAware API to return 500 Internal Server Error
        stubFor(get(urlPathEqualTo("/flights/" + testIdent))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": \"Internal Server Error\"}")));

        log.info("✅ FlightAware API mocked to return 500");

        // ACT & ASSERT
        log.info("🚀 Requesting flight when API is down...");

        assertThatThrownBy(() ->
                restTemplate.getForEntity(
                        API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent,
                        FlightDataResponse.class
                )
        )
                .isInstanceOfAny(HttpServerErrorException.class, RestClientException.class)
                .satisfies(ex -> {
                    if (ex instanceof HttpServerErrorException) {
                        HttpServerErrorException httpEx = (HttpServerErrorException) ex;
                        assertThat(httpEx.getStatusCode().is5xxServerError()).isTrue();
                        log.info("✅ Received expected 5xx Server Error");
                    } else {
                        log.info("✅ Received RestClientException (connection failure)");
                    }
                });

        log.info("🎉 TEST 4 COMPLETE: API failure handling verified!");
    }

    // ===========================================
    // TEST 5: ERROR HANDLING - OPENAI RATE LIMIT
    // ===========================================

    @Test
    @Order(5)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Test 5: Should honor Retry-After when OpenAI rate limits")
    void shouldHandleOpenAIRateLimitWithExponentialBackoff() throws Exception {
        log.info("📋 TEST 5: Error Handling - OpenAI Rate Limit");
        log.info("-".repeat(80));

        // ARRANGE
        String testIdent = "UAL999";
        String faFlightId = "UAL999-1678886402-airline-0999";

        log.info("🎯 Testing OpenAI rate limit handling for flight: {}", testIdent);

        // Mock FlightAware API (successful)
        stubFor(get(urlPathEqualTo("/flights/" + testIdent))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createFlightDataJson(testIdent, faFlightId))));

        log.info("✅ FlightAware API mocked (success)");

        // Mock OpenAI API - First request returns 429 (rate limit), second succeeds
        stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("Rate Limit Recovery")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Retry-After", "2")
                        .withBody("{\"error\": {\"message\": \"Rate limit exceeded\", \"type\": \"rate_limit_error\"}}"))
                .willSetStateTo("Rate Limit Passed"));

        stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .inScenario("Rate Limit Recovery")
                .whenScenarioStateIs("Rate Limit Passed")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(createOpenAIResponseJson(testIdent))));

        log.info("✅ OpenAI API mocked (rate limit → success)");

        // ACT - Trigger the flow
        FlightDataResponse candidate = findSingleCandidate(testIdent);
        assertThat(candidate.getFaFlightId()).isEqualTo(faFlightId);
        log.info("🚀 Requesting flight data (will trigger async LLM processing)...");
        ResponseEntity<FlightDataResponse> flightDataResponse = restTemplate.getForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent,
                FlightDataResponse.class
        );

        // ASSERT - Flight data retrieved
        assertThat(flightDataResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        log.info("✅ Flight data retrieved");

        long queuedAt = System.nanoTime();
        queueSelectedSummary(testIdent, faFlightId);

        // Wait for LLM summary to be generated (with retry logic)
        log.info("⏳ Waiting for LLM summary with retry logic (up to 45s)...");

        await()
                .atMost(Duration.ofSeconds(45))
                .pollInterval(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    try {
                        ResponseEntity<FlightSummaryResponse> summaryResponse = restTemplate.getForEntity(
                                API_GATEWAY_BASE_URL + "/api/v1/flight/" + testIdent + "/summary/" + faFlightId,
                                FlightSummaryResponse.class
                        );

                        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(summaryResponse.getBody()).isNotNull();
                        assertThat(summaryResponse.getBody().getSummaryText())
                                .isNotNull()
                                .contains("United Flight 999");

                        log.info("✅ LLM summary successfully generated after rate limit recovery");
                    } catch (RestClientException e) {
                        log.debug("Summary not ready yet (rate limit recovery in progress), retrying...");
                        throw e;
                    }
                });

        // Verify retry happened (OpenAI API should be called at least twice)
        verify(moreThanOrExactly(2), postRequestedFor(urlPathMatching("/v1/chat/completions")));
        long recoveryDelayMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
        assertThat(recoveryDelayMs).isGreaterThanOrEqualTo(2_000L);
        log.info("✅ Verified: OpenAI API was retried after rate limit");

        log.info("🎉 TEST 5 COMPLETE: Rate limit recovery verified!");
    }

    // ===========================================
    // HELPER METHODS
    // ===========================================

    private FlightDataResponse findSingleCandidate(String ident) {
        ResponseEntity<List<FlightDataResponse>> response = restTemplate.exchange(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + ident + "/candidates",
                org.springframework.http.HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FlightDataResponse>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        return response.getBody().get(0);
    }

    private void queueSelectedSummary(String ident, String faFlightId) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                API_GATEWAY_BASE_URL + "/api/v1/flight/" + ident + "/instance/" + faFlightId + "/summary/retry",
                null,
                Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("fa_flight_id", faFlightId);
    }

    /**
     * Create mock FlightAware API JSON response
     */
    private String createFlightDataJson(String ident, String faFlightId) {
        return String.format("""
                {"flights": [{
                    "fa_flight_id": "%s",
                    "ident": "%s",
                    "status": "En-Route / In Flight",
                    "scheduled_out": "2023-03-15T12:00:00Z",
                    "actual_out": "2023-03-15T12:05:00Z",
                    "scheduled_in": "2023-03-15T18:30:00Z",
                    "origin": "KORD",
                    "destination": "KLAX",
                    "aircraft_type": "B738",
                    "latitude": 39.8,
                    "longitude": -98.6,
                    "altitude": 35000,
                    "groundspeed": 450
                }]}
                """, faFlightId, ident);
    }

    /**
     * Create mock OpenAI API JSON response
     */
    private String createOpenAIResponseJson(String ident) {
        String flightNumber = ident.substring(3); // Extract number part
        return String.format("""
                {
                    "id": "chatcmpl-test",
                    "object": "chat.completion",
                    "created": %d,
                    "model": "gpt-6-luna",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "United Flight %s (%s) is currently En Route from Chicago (KORD) to Los Angeles (KLAX). The flight departed 5 minutes late at 12:05 UTC and is scheduled to arrive at 18:30 UTC."
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": 100,
                        "completion_tokens": 50,
                        "total_tokens": 150
                    }
                }
                """, Instant.now().getEpochSecond(), flightNumber, ident);
    }
}

