package com.airlinetracker.llmsummary.client;

import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.config.OpenAIRateLimitConfig;
import com.airlinetracker.llmsummary.exception.OpenAIProviderException;
import com.airlinetracker.llmsummary.exception.OpenAIRateLimitExceededException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Instant;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TDD Test for OpenAIClient.
 * Uses WireMock to simulate OpenAI API responses.
 *
 * RED Phase: This test will fail until OpenAIClient is implemented.
 */
@DisplayName("OpenAIClient Tests")
class OpenAIClientTest {

    private WireMockServer wireMockServer;
    private OpenAIClient openAIClient;
    private static final String TEST_API_KEY = "test-api-key-12345";

    @BeforeEach
    void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Create OpenAIClient pointing to WireMock server
        String baseUrl = "http://localhost:" + wireMockServer.port();
        openAIClient = new OpenAIClient(baseUrl, TEST_API_KEY, "gpt-6-luna", 150, 0.7);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Should generate summary for valid flight data")
    void testGenerateSummary_Success() {
        // Given: Mock OpenAI API response
        stubFor(post(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer " + TEST_API_KEY))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id": "chatcmpl-123",
                                  "object": "chat.completion",
                                  "created": 1677652288,
                                  "model": "gpt-6-luna",
                                  "choices": [{
                                    "index": 0,
                                    "message": {
                                      "role": "assistant",
                                      "content": "United Flight 123 (UAL123) is currently En Route from Chicago O'Hare (KORD) to Los Angeles (KLAX). The flight departed on time and is flying at 35,000 feet."
                                    },
                                    "finish_reason": "stop"
                                  }],
                                  "usage": {
                                    "prompt_tokens": 50,
                                    "completion_tokens": 30,
                                    "total_tokens": 80
                                  }
                                }
                                """)));

        // Create test flight data
        FlightData flightData = createTestFlightData();

        // When: Calling generateSummary
        String summary = openAIClient.generateSummary(flightData);

        // Then: Should return the summary
        assertThat(summary).isNotNull();
        assertThat(summary).contains("United Flight 123");
        assertThat(summary).contains("UAL123");
        assertThat(summary).contains("En Route");
        assertThat(summary).contains("Chicago O'Hare");
        assertThat(summary).contains("Los Angeles");

        // Verify the request was made correctly
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer " + TEST_API_KEY))
                .withHeader("Content-Type", equalTo("application/json")));
    }

    @Test
    @DisplayName("Should stop before provider request when the shared quota is exhausted")
    void testGenerateSummary_QuotaExceeded() {
        OpenAIRateLimitConfig.RateLimiter limiter = mock(OpenAIRateLimitConfig.RateLimiter.class);
        when(limiter.allowRequest()).thenReturn(false);
        OpenAIClient limitedClient = new OpenAIClient(
                "http://localhost:" + wireMockServer.port(), TEST_API_KEY, "gpt-6-luna", 150, 0.7,
                CircuitBreakerRegistry.ofDefaults(), limiter, false);

        assertThatThrownBy(() -> limitedClient.generateSummary(createTestFlightData()))
                .isInstanceOf(OpenAIRateLimitExceededException.class);
        verify(0, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    @DisplayName("Should handle OpenAI API error (500)")
    void testGenerateSummary_ApiError() {
        // Given: Mock OpenAI API error response
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        FlightData flightData = createTestFlightData();

        // When/Then: Should throw exception
        assertThatThrownBy(() -> openAIClient.generateSummary(flightData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OpenAI API");
    }

    @Test
    @DisplayName("Should capture Retry-After without retaining the provider error body")
    void testGenerateSummary_RateLimitRetryAfter() {
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Retry-After", "2")
                        .withBody("private provider diagnostic")));

        Throwable failure = catchThrowable(() -> openAIClient.generateSummary(createTestFlightData()));

        assertThat(failure).isInstanceOf(RuntimeException.class);
        assertThat(failure.getMessage()).doesNotContain("private provider diagnostic");
        assertThat(failure.getCause()).isInstanceOf(OpenAIProviderException.class);
        OpenAIProviderException providerFailure = (OpenAIProviderException) failure.getCause();
        assertThat(providerFailure.getStatusCode()).isEqualTo(429);
        assertThat(providerFailure.getRetryAfter()).isEqualTo(Duration.ofSeconds(2));
        assertThat(providerFailure.getMessage()).doesNotContain("private provider diagnostic");
    }

    @Test
    @DisplayName("Should handle timeout")
    void testGenerateSummary_Timeout() {
        // Given: Mock delayed response
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(15000) // 15 seconds delay
                        .withBody("{}")));

        FlightData flightData = createTestFlightData();

        // When/Then: Should throw timeout exception
        assertThatThrownBy(() -> openAIClient.generateSummary(flightData))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should handle unauthorized (401)")
    void testGenerateSummary_Unauthorized() {
        // Given: Mock unauthorized response
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": {\"message\": \"Invalid API key\"}}")));

        FlightData flightData = createTestFlightData();

        // When/Then: Should throw exception (wrapped in RuntimeException by our client)
        assertThatThrownBy(() -> openAIClient.generateSummary(flightData))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OpenAI API");
    }

    @Test
    @DisplayName("Should format prompt correctly with flight data")
    void testGenerateSummary_PromptFormat() {
        // Given: Mock response
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {
                                      "content": "Test summary"
                                    }
                                  }]
                                }
                                """)));

        FlightData flightData = createTestFlightData();

        // When: Generate summary
        String summary = openAIClient.generateSummary(flightData);

        // Then: Verify summary was generated
        assertThat(summary).isEqualTo("Test summary");

        // Verify request body contains flight data
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("UAL123"))
                .withRequestBody(containing("KORD"))
                .withRequestBody(containing("KLAX")));
    }

    @Test
    @DisplayName("Should include model and parameters in request")
    void testGenerateSummary_RequestParameters() {
        // Given: Mock response
        stubFor(post(urlEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "choices": [{
                                    "message": {
                                      "content": "Summary"
                                    }
                                  }]
                                }
                                """)));

        FlightData flightData = createTestFlightData();

        // When: Generate summary
        openAIClient.generateSummary(flightData);

        // Then: Verify request includes model and parameters
        verify(postRequestedFor(urlEqualTo("/chat/completions"))
                .withRequestBody(containing("gpt-6-luna"))
                .withRequestBody(containing("max_completion_tokens")));
    }

    @Test
    @DisplayName("Demo profile returns deterministic text without calling the provider")
    void testDemoSummary_DoesNotCallProvider() {
        OpenAIClient demoClient = new OpenAIClient("http://localhost:" + wireMockServer.port(), "",
                "gpt-6-luna", 150, 0.7, CircuitBreakerRegistry.ofDefaults(), true);

        String result = demoClient.generateSummary(createTestFlightData());

        assertThat(result).contains("Demo summary", "UAL123", "Chicago", "Los Angeles");
        verify(0, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    /**
     * Helper method to create test flight data.
     */
    private FlightData createTestFlightData() {
        return FlightData.builder()
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .status("En-Route / In Flight")
                .scheduledOut(Instant.parse("2025-11-10T09:00:00Z"))
                .actualOut(Instant.parse("2025-11-10T10:00:00Z"))
                .scheduledIn(Instant.parse("2025-11-10T14:00:00Z"))
                .actualIn(null)
                .origin("KORD")
                .originCity("Chicago")
                .destination("KLAX")
                .destinationCity("Los Angeles")
                .aircraftType("B738")
                .latitude(39.8283)
                .longitude(-98.5795)
                .altitude(35000)
                .groundspeed(450)
                .build();
    }
}
