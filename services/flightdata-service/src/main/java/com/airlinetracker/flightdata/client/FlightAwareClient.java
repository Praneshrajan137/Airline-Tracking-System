package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.config.RateLimitConfig;
import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

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
                .retrieve()
                .onStatus(
                        status -> status == HttpStatus.NOT_FOUND,
                        response -> {
                            log.warn("Flight not found: {}", ident);
                            return Mono.error(new FlightNotFoundException(ident));
                        }
                )
                .onStatus(
                        status -> status == HttpStatus.TOO_MANY_REQUESTS,
                        response -> {
                            log.error("FlightAware API rate limit (429) - Provider side");
                            return Mono.error(new RateLimitExceededException("FlightAware API rate limit exceeded"));
                        }
                )
                .onStatus(
                        status -> status.is5xxServerError(),
                        response -> {
                            log.error("FlightAware API server error for ident: {}", ident);
                            return Mono.error(new RuntimeException("FlightAware API error"));
                        }
                )
                .bodyToMono(FlightData.class)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .doOnSuccess(flightData -> 
                    log.info("✅ Successfully fetched flight data: {} ({})", 
                            flightData.getIdent(), flightData.getStatus()))
                .doOnError(error -> 
                    log.error("❌ Error fetching flight data for {}: {}", ident, error.getMessage()));
    }
}

