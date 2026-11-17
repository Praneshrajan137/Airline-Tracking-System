package com.airlinetracker.llmsummary.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j Circuit Breaker configuration for OpenAI API calls.
 *
 * Circuit breaker thresholds:
 * - failureRateThreshold: 50% — open circuit when half of calls fail
 * - slidingWindowSize: 5 — evaluate last 5 calls
 * - waitDurationInOpenState: 30s — wait before trying again
 * - permittedNumberOfCallsInHalfOpenState: 2 — test with 2 calls
 */
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.ofDefaults();
    }
}
