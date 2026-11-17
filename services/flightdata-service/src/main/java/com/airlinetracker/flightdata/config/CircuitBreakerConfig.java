package com.airlinetracker.flightdata.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j Circuit Breaker configuration for FlightAware API calls.
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

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        return TimeLimiterRegistry.ofDefaults();
    }
}
