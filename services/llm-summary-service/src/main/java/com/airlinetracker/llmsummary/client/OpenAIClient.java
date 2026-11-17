package com.airlinetracker.llmsummary.client;

import com.airlinetracker.llmsummary.config.OpenAIRateLimitConfig;
import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.exception.OpenAIProviderException;
import com.airlinetracker.llmsummary.exception.OpenAIRateLimitExceededException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Client for OpenAI API to generate flight summaries.
 * Uses a configured supported chat model for human-readable summaries.
 * 
 * Implements prompt template from docs/LLM-PROMPT-TEMPLATE.md
 */
@Component
@Slf4j
public class OpenAIClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final boolean demoMode;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;
    private final OpenAIRateLimitConfig.RateLimiter rateLimiter;

    private static final int TIMEOUT_SECONDS = 10;
    private static final String SYSTEM_PROMPT = """
            You are an expert aviation assistant. Your sole purpose is to summarize raw flight data JSON into a clear, human-readable status update.
            
            **Rules:**
            1. Concise: Maximum 2-3 sentences.
            2. Content: Include flight number, origin, destination, and current status.
            3. Tone: Informative and professional.
            """;

    @Autowired
    public OpenAIClient(
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-6-luna}") String model,
            @Value("${openai.max-tokens:150}") int maxTokens,
            @Value("${openai.temperature:0.7}") double temperature,
            CircuitBreakerRegistry circuitBreakerRegistry,
            OpenAIRateLimitConfig.RateLimiter rateLimiter,
            @Value("${demo.enabled:false}") boolean demoMode) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.demoMode = demoMode;
        this.rateLimiter = rateLimiter;
        if (!demoMode && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("OPENAI_API_KEY is required unless the demo profile is enabled");
        }
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // Enable Java 8 date/time support

        // Initialize circuit breaker for OpenAI API
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("openAiApi",
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(5)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build());

        log.info("=== OPENAI CLIENT INITIALIZED ===");
        log.info("Base URL: {}", baseUrl);
        log.info("Model: {}", model);
        log.info("Max completion tokens: {}", maxTokens);
    }

    /** Constructor kept for isolated WireMock tests. */
    public OpenAIClient(String baseUrl, String apiKey, String model, int maxTokens, double temperature) {
        this(baseUrl, apiKey, model, maxTokens, temperature, CircuitBreakerRegistry.ofDefaults(),
                new OpenAIRateLimitConfig.RateLimiter(null, 0, 0, 0, false), false);
    }

    /** Constructor kept for demo-mode unit tests without a Redis dependency. */
    public OpenAIClient(String baseUrl, String apiKey, String model, int maxTokens, double temperature,
                        CircuitBreakerRegistry circuitBreakerRegistry, boolean demoMode) {
        this(baseUrl, apiKey, model, maxTokens, temperature, circuitBreakerRegistry,
                new OpenAIRateLimitConfig.RateLimiter(null, 0, 0, 0, false), demoMode);
    }

    /**
     * Generate a human-readable summary from flight data with circuit breaker protection.
     *
     * @param flightData Flight data to summarize
     * @return Human-readable summary string or fallback message if circuit is open
     * @throws CallNotPermittedException when circuit is open (OpenAI API is down)
     */
    public String generateSummary(FlightData flightData) {
        log.debug("Generating summary for flight: {}", flightData.getIdent());

        if (demoMode) {
            String origin = flightData.getOriginCity() == null ? flightData.getOrigin() : flightData.getOriginCity();
            String destination = flightData.getDestinationCity() == null ? flightData.getDestination() : flightData.getDestinationCity();
            return String.format("Demo summary: %s is %s from %s to %s. This is a deterministic sample, not live airline data.",
                    flightData.getIdent(), flightData.getStatus(), origin, destination);
        }

        if (!rateLimiter.allowRequest()) {
            throw new OpenAIRateLimitExceededException("OpenAI request limit reached. Please retry later.");
        }

        return circuitBreaker.executeSupplier(() -> {
            try {
                // Convert flight data to JSON string
                String flightDataJson = objectMapper.writeValueAsString(flightData);

                // Create user prompt with flight data
                String userPrompt = "Summarize this flight data:\n\n" + flightDataJson;

                // Build OpenAI API request
                OpenAIRequest request = new OpenAIRequest(
                        model,
                        List.of(
                                new Message("system", SYSTEM_PROMPT),
                                new Message("user", userPrompt)
                        ),
                        maxTokens
                );

                // Call OpenAI API
                log.info("Calling OpenAI API: {}/chat/completions", webClient.toString().contains("baseUrl") ? "configured" : "http://wiremock:8080/v1");
                OpenAIResponse response = webClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                            return clientResponse.bodyToMono(Void.class).then(Mono.error(
                                    new OpenAIProviderException(
                                            clientResponse.statusCode().value(),
                                            retryAfter(clientResponse))));
                        })
                        .onStatus(status -> status.is5xxServerError(), serverResponse -> {
                            return serverResponse.bodyToMono(Void.class).then(Mono.error(
                                    new OpenAIProviderException(serverResponse.statusCode().value(), null)));
                        })
                        .bodyToMono(OpenAIResponse.class)
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .block();

                // Extract summary from response
                if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                    String summary = response.getChoices().get(0).getMessage().getContent();
                    log.info("Generated summary for flight {}", flightData.getIdent());
                    return summary;
                } else {
                    log.error("Empty response from OpenAI API");
                    throw new RuntimeException("OpenAI API returned empty response");
                }

            } catch (CallNotPermittedException e) {
                throw e; // Re-throw to be handled by circuit breaker
            } catch (Exception e) {
                log.error("Failed to generate summary for flight {} ({})",
                        flightData.getIdent(), e.getClass().getSimpleName());
                throw new RuntimeException("OpenAI API request failed", e);
            }
        });
    }

    private static Duration retryAfter(ClientResponse response) {
        return response.headers().header("Retry-After").stream()
                .findFirst()
                .map(OpenAIClient::parseRetryAfter)
                .orElse(null);
    }

    private static Duration parseRetryAfter(String value) {
        try {
            long seconds = Long.parseLong(value.trim());
            return Duration.ofSeconds(Math.min(Math.max(seconds, 0), 60));
        } catch (NumberFormatException ignored) {
            try {
                Duration delay = Duration.between(
                        java.time.Instant.now(),
                        java.time.ZonedDateTime.parse(value,
                                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return Duration.ofMillis(Math.min(Math.max(delay.toMillis(), 0), 60_000));
            } catch (java.time.DateTimeException ignoredDate) {
                return null;
            }
        }
    }

    /**
     * OpenAI API request structure.
     */
    @Data
    private static class OpenAIRequest {
        private final String model;
        private final List<Message> messages;
        @JsonProperty("max_completion_tokens")
        private final int maxTokens;

        private OpenAIRequest(String model, List<Message> messages, int maxTokens) {
            this.model = model;
            this.messages = messages;
            this.maxTokens = maxTokens;
        }
    }

    /**
     * Message structure for OpenAI chat completion.
     */
    @Data
    private static class Message {
        private final String role;
        private final String content;
    }

    /**
     * OpenAI API response structure.
     */
    @Data
    private static class OpenAIResponse {
        private List<Choice> choices;
    }

    /**
     * Choice structure in OpenAI response.
     */
    @Data
    private static class Choice {
        private Message message;
    }
}

