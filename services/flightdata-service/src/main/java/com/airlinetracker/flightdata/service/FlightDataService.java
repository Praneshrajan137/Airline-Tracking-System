package com.airlinetracker.flightdata.service;

import com.airlinetracker.flightdata.client.FlightAwareClient;
import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.SummaryEventUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Retrieves and caches candidates; only an explicitly selected instance is sent for summary generation. */
@Service
public class FlightDataService {
    private final FlightAwareClient flightAwareClient;
    private final KafkaTemplate<String, FlightData> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Counter cacheMissCounter;
    private final Counter cacheHitCounter;
    private final Timer apiDurationTimer;

    @Value("${kafka.topic.flight-data-events:flight-data-events}")
    private String flightDataTopic = "flight-data-events";

    public FlightDataService(FlightAwareClient flightAwareClient,
                             KafkaTemplate<String, FlightData> kafkaTemplate,
                             MeterRegistry meterRegistry,
                             CircuitBreakerRegistry cbRegistry) {
        this.flightAwareClient = flightAwareClient;
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = cbRegistry.circuitBreaker("flightAwareApi",
                CircuitBreakerConfig.custom().failureRateThreshold(50).slidingWindowSize(5)
                        .minimumNumberOfCalls(5).waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true).build());
        this.cacheHitCounter = Counter.builder("flight_cache_hits")
                .description("Number of flight data cache hits")
                .tag("service", "flightdata-service").register(meterRegistry);
        this.cacheMissCounter = Counter.builder("flight_cache_misses")
                .description("Number of flight data cache misses")
                .tag("service", "flightdata-service").register(meterRegistry);
        this.apiDurationTimer = Timer.builder("flightaware_api_duration")
                .description("Duration of flight provider calls")
                .tag("service", "flightdata-service").register(meterRegistry);
    }

    @Cacheable(value = "flight-candidates", key = "#ident.toUpperCase()")
    public List<FlightData> getFlightsByIdent(String ident) {
        cacheMissCounter.increment();
        List<FlightData> flights = circuitBreaker.executeSupplier(() ->
                apiDurationTimer.record(() -> flightAwareClient.getFlightsByIdent(ident).block()));
        if (flights == null || flights.isEmpty()) throw new FlightNotFoundException(ident);

        Instant checkedAt = Instant.now();
        for (FlightData flight : flights) {
            if (flight.getCheckedAt() == null) flight.setCheckedAt(checkedAt);
            if (flight.getDataSource() == null) flight.setDataSource("FlightAware AeroAPI");
        }
        return flights;
    }

    /** Kept for existing service integrations; the controller rejects ambiguous identifiers. */
    public FlightData getFlightByIdent(String ident) {
        List<FlightData> flights = getFlightsByIdent(ident);
        if (flights.size() != 1) {
            throw new AmbiguousFlightException(ident, flights.size());
        }
        return flights.get(0);
    }

    /** Queue the one selected flight instance for initial summary generation or recovery. */
    public void requestSummary(FlightData flightData) {
        try {
            CompletableFuture<SendResult<String, FlightData>> published = kafkaTemplate.send(
                    flightDataTopic, flightData.getFaFlightId(), flightData);
            if (published == null) throw new IllegalStateException("Kafka returned no publish result");
            published.get(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SummaryEventUnavailableException("Summary event publication was interrupted", exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            throw new SummaryEventUnavailableException("Summary event could not be delivered", exception);
        }
    }

    /** Uncached method retained for metric-focused tests. */
    public FlightData getFlightByIdentUncached(String ident) {
        cacheMissCounter.increment();
        List<FlightData> flights = circuitBreaker.executeSupplier(() ->
                apiDurationTimer.record(() -> flightAwareClient.getFlightsByIdent(ident).block()));
        if (flights == null || flights.isEmpty()) throw new FlightNotFoundException(ident);
        return flights.get(0);
    }

    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

}
