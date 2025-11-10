package com.airlinetracker.llmsummary.client;

import com.airlinetracker.llmsummary.dto.FlightData;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for OpenAI API to generate flight summaries.
 * Uses GPT-3.5-turbo to convert flight data JSON into human-readable summaries.
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
    private final ObjectMapper objectMapper;

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
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model:gpt-3.5-turbo}") String model,
            @Value("${openai.max-tokens:150}") int maxTokens,
            @Value("${openai.temperature:0.7}") double temperature) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule()); // Enable Java 8 date/time support
        log.info("OpenAIClient initialized: model={}, maxTokens={}, temperature={}", 
                 model, maxTokens, temperature);
    }

    /**
     * Generate a human-readable summary from flight data.
     * 
     * @param flightData Flight data to summarize
     * @return Human-readable summary string
     * @throws RuntimeException if API call fails
     */
    public String generateSummary(FlightData flightData) {
        log.debug("Generating summary for flight: {}", flightData.getIdent());

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
                    maxTokens,
                    temperature
            );

            // Call OpenAI API
            OpenAIResponse response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                        log.error("OpenAI API 4xx error: {}", clientResponse.statusCode());
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "OpenAI API client error: " + clientResponse.statusCode() + " - " + body)));
                    })
                    .onStatus(status -> status.is5xxServerError(), serverResponse -> {
                        log.error("OpenAI API 5xx error: {}", serverResponse.statusCode());
                        return Mono.error(new RuntimeException(
                                "OpenAI API server error: " + serverResponse.statusCode()));
                    })
                    .bodyToMono(OpenAIResponse.class)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .block();

            // Extract summary from response
            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                String summary = response.getChoices().get(0).getMessage().getContent();
                log.info("Generated summary for flight {}: {}", flightData.getIdent(), summary);
                return summary;
            } else {
                log.error("Empty response from OpenAI API");
                throw new RuntimeException("OpenAI API returned empty response");
            }

        } catch (Exception e) {
            log.error("Failed to generate summary for flight {}: {}", flightData.getIdent(), e.getMessage(), e);
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * OpenAI API request structure.
     */
    @Data
    private static class OpenAIRequest {
        private final String model;
        private final List<Message> messages;
        @JsonProperty("max_tokens")
        private final int maxTokens;
        private final double temperature;
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

