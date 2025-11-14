package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.config.RateLimitConfig;
import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.RateLimitExceededException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

/**
 * FlightAware AeroAPI Client with Rate Limiting
 *
 * Source: PRD.md FR-1 - Third-Party Flight Data Integration
 *
 * Responsibilities:
 * - Call FlightAware AeroAPI to retrieve real-time flight data
 * - Enforce rate limits to protect API free tier ($5 limit)
 * - Add x-apikey authentication header
 * - Handle errors (404, 429, 500, timeout)
 * - Parse JSON response to FlightData DTO
 *
 * Security:
 * - Rate limiting prevents API key misuse
 * - Protects against cost overruns on free tier
 *
 * API Documentation: https://aeroapi.flightaware.com/aeroapi
 */
@Slf4j
@Component
public class FlightAwareClient {

    private final WebClient webClient;
    private final String apiKey;
    private final RateLimitConfig.RateLimiter rateLimiter;

    private static final int TIMEOUT_SECONDS = 5;

    /**
     * Constructor with WebClient, API key, and RateLimiter injection
     */
    @Autowired
    public FlightAwareClient(
            @Value("${flightaware.base-url:https://aeroapi.flightaware.com/aeroapi}") String baseUrl,
            @Value("${flightaware.api-key}") String apiKey,
            RateLimitConfig.RateLimiter rateLimiter) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        log.info("FlightAwareClient initialized with baseUrl: {} and rate limiting enabled", baseUrl);
    }

    /**
     * Constructor for testing (allows injecting custom WebClient)
     */
    public FlightAwareClient(WebClient webClient, String apiKey, RateLimitConfig.RateLimiter rateLimiter) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Get flight data by ident (flight number)
     *
     * @param ident Flight identifier (e.g., "UAL123")
     * @return Mono<FlightData> containing flight information
     * @throws FlightNotFoundException if flight is not found (404)
     * @throws RateLimitExceededException if rate limit is exceeded
     * @throws RuntimeException for other errors
     */
    public Mono<FlightData> getFlightByIdent(String ident) {
        log.debug("Fetching flight data for ident: {}", ident);

        // Check rate limit BEFORE making API call
        if (!rateLimiter.allowRequest()) {
            RateLimitConfig.UsageStats stats = rateLimiter.getUsageStats();
            String errorMsg = String.format(
                "⚠️ FlightAware API rate limit exceeded! %s - Protecting your $5 free tier",
                stats.toString()
            );
            log.error(errorMsg);
            return Mono.error(new RateLimitExceededException(errorMsg));
        }

        // Log current usage for monitoring
        RateLimitConfig.UsageStats stats = rateLimiter.getUsageStats();
        log.info("FlightAware API call allowed - Current usage: {}", stats);

        return webClient
                .get()
                .uri("/flights/{ident}", ident)
                .header("x-apikey", apiKey)
                .exchangeToMono(response -> {
                    log.info("🔍 FlightAware API response status: {}", response.statusCode());
                    return response.bodyToMono(String.class)
                            .doOnNext(body -> log.info("🔍 Raw FlightAware response body: {}", body))
                            .flatMap(body -> {
                                try {
                                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                                        new com.fasterxml.jackson.databind.ObjectMapper();
                                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                                    mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                                    FlightAwareResponse parsed = mapper.readValue(body, FlightAwareResponse.class);
                                    return Mono.just(parsed);
                                } catch (Exception e) {
                                    log.error("❌ Failed to parse FlightAware response: {}", e.getMessage(), e);
                                    return Mono.error(e);
                                }
                            });
                })

                .doOnNext(response -> {
                    log.info("🔍 FlightAware API Response - flights array size: {}, num_pages: {}",
                            (response != null && response.getFlights() != null) ? response.getFlights().size() : "null",
                            (response != null) ? response.getNumPages() : "null");
                    if (response != null && response.getFlights() != null && !response.getFlights().isEmpty()) {
                        log.info("🔍 First flight in array - ident: {}, status: {}, fa_flight_id: {}",
                                response.getFlights().get(0).getIdent(),
                                response.getFlights().get(0).getStatus(),
                                response.getFlights().get(0).getFaFlightId());
                    }
                })
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .map(response -> {
                    // FlightAware returns array of flights, extract first one
                    log.info("🔍 Mapping response - response null? {}, flights null? {}, flights empty? {}",
                            response == null,
                            (response != null && response.getFlights() == null),
                            (response != null && response.getFlights() != null && response.getFlights().isEmpty()));

                    if (response != null && response.getFlights() != null && !response.getFlights().isEmpty()) {
                        FlightData flight = response.getFlights().get(0);
                        log.info("✅ Successfully fetched flight data: {} ({})",
                                flight.getIdent(), flight.getStatus());
                        return flight;
                    } else {
                        log.error("❌ FlightAware returned empty or null flights array for: {} - Response: {}",
                                ident, response);
                        throw new FlightNotFoundException(ident);
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Error fetching flight data for {}: {} (Type: {})",
                            ident, error.getMessage(), error.getClass().getSimpleName());
                    if (error.getCause() != null) {
                        log.error("❌ Caused by: {}", error.getCause().getMessage());
                    }
                });
    }

    /**
     * FlightAware API response wrapper.
     * The /flights/{ident} endpoint returns an array of flights, not a single flight.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class FlightAwareResponse {
        @JsonProperty("flights")
        private List<FlightData> flights;

        @JsonProperty("links")
        private Object links;

        @JsonProperty("num_pages")
        private Integer numPages;
    }
}
