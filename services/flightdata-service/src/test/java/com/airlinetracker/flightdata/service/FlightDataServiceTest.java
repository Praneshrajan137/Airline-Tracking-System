package com.airlinetracker.flightdata.service;

import com.airlinetracker.flightdata.client.FlightAwareClient;
import com.airlinetracker.flightdata.dto.FlightData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Test for FlightDataService - Custom Metrics
 * 
 * Source: PRD.md NFR-4 (Observability)
 * 
 * Required Custom Metrics:
 * 1. flight_cache_hits_total - Counter for cache hits
 * 2. flight_cache_misses_total - Counter for cache misses
 * 3. flightaware_api_duration_seconds - Timer for API call duration
 * 
 * Test Pattern: RED → GREEN → REFACTOR
 */
@ExtendWith(MockitoExtension.class)
class FlightDataServiceTest {

    @Mock
    private FlightAwareClient flightAwareClient;

    @Mock
    private KafkaTemplate<String, FlightData> kafkaTemplate;

    private MeterRegistry meterRegistry;
    private FlightDataService flightDataService;

    @BeforeEach
    void setUp() {
        // Use SimpleMeterRegistry for testing
        meterRegistry = new SimpleMeterRegistry();
        
        // Create service with mocked dependencies and real MeterRegistry
        flightDataService = new FlightDataService(
            flightAwareClient,
            kafkaTemplate,
            meterRegistry
        );
    }

    /**
     * TEST 1: Cache Miss Counter
     * 
     * Requirement (PRD.md NFR-4):
     * - Counter: flight_cache_misses_total
     * - Incremented when: Cache miss occurs (API called)
     * 
     * Scenario:
     * - Call getFlightByIdent() which results in cache miss
     * - Counter should increment by 1
     */
    @Test
    void shouldIncrementCacheMissCounter_WhenCacheMissOccurs() {
        // Arrange
        String ident = "UAL123";
        FlightData mockFlightData = FlightData.builder()
                .faFlightId("UAL123-1234567890")
                .ident("UAL123")
                .status("En-Route")
                .origin("KORD")
                .destination("KLAX")
                .scheduledOut(Instant.now())
                .build();

        when(flightAwareClient.getFlightByIdent(ident))
                .thenReturn(Mono.just(mockFlightData));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(null);

        // Get initial count (note: Micrometer adds _total suffix for Prometheus export)
        Counter cacheMissCounter = meterRegistry.counter("flight_cache_misses", "service", "flightdata-service");
        double initialCount = cacheMissCounter.count();

        // Act: Simulate cache miss (direct call to service, bypassing @Cacheable)
        flightDataService.getFlightByIdentUncached(ident);

        // Assert: Counter incremented
        double finalCount = cacheMissCounter.count();
        assertThat(finalCount).isEqualTo(initialCount + 1);
    }

    /**
     * TEST 2: Cache Hit Counter
     * 
     * Requirement (PRD.md NFR-4):
     * - Counter: flight_cache_hits_total
     * - Incremented when: Cache hit occurs (no API call)
     * 
     * Note: This will be tested in integration test with real cache
     * Here we test the metric increment logic directly
     */
    @Test
    void shouldIncrementCacheHitCounter_WhenCacheHitOccurs() {
        // Arrange
        Counter cacheHitCounter = meterRegistry.counter("flight_cache_hits", "service", "flightdata-service");
        double initialCount = cacheHitCounter.count();

        // Act: Simulate cache hit (method that increments counter)
        flightDataService.recordCacheHit();

        // Assert: Counter incremented
        double finalCount = cacheHitCounter.count();
        assertThat(finalCount).isEqualTo(initialCount + 1);
    }

    /**
     * TEST 3: FlightAware API Duration Timer
     * 
     * Requirement (PRD.md NFR-4):
     * - Timer: flightaware_api_duration_seconds
     * - Records: Duration of FlightAware API calls
     * - Purpose: Monitor API performance (PRD target: < 3s for cache miss)
     * 
     * Scenario:
     * - Call API via service
     * - Timer should record the duration
     */
    @Test
    void shouldRecordApiCallDuration_WhenCallingFlightAwareApi() {
        // Arrange
        String ident = "DAL456";
        FlightData mockFlightData = FlightData.builder()
                .faFlightId("DAL456-1234567890")
                .ident("DAL456")
                .status("Scheduled")
                .origin("KATL")
                .destination("KJFK")
                .scheduledOut(Instant.now())
                .build();

        when(flightAwareClient.getFlightByIdent(ident))
                .thenReturn(Mono.just(mockFlightData));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(null);

        // Get timer before call
        Timer apiTimer = meterRegistry.timer("flightaware_api_duration", "service", "flightdata-service");
        long initialCount = apiTimer.count();

        // Act: Call service (which calls FlightAware API)
        flightDataService.getFlightByIdentUncached(ident);

        // Assert: Timer recorded the call
        long finalCount = apiTimer.count();
        assertThat(finalCount).isEqualTo(initialCount + 1);
        
        // Assert: Duration was recorded (should be > 0)
        assertThat(apiTimer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    /**
     * TEST 4: Multiple Cache Misses
     * 
     * Verify counter increments correctly for multiple calls
     */
    @Test
    void shouldIncrementCacheMissCounterMultipleTimes_WhenMultipleCacheMissesOccur() {
        // Arrange
        FlightData mockFlightData = FlightData.builder()
                .faFlightId("TEST123-1234567890")
                .ident("TEST123")
                .status("Landed")
                .build();

        when(flightAwareClient.getFlightByIdent(anyString()))
                .thenReturn(Mono.just(mockFlightData));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(null);

        Counter cacheMissCounter = meterRegistry.counter("flight_cache_misses", "service", "flightdata-service");
        double initialCount = cacheMissCounter.count();

        // Act: Make 3 calls (3 cache misses)
        flightDataService.getFlightByIdentUncached("TEST123");
        flightDataService.getFlightByIdentUncached("TEST456");
        flightDataService.getFlightByIdentUncached("TEST789");

        // Assert: Counter incremented 3 times
        double finalCount = cacheMissCounter.count();
        assertThat(finalCount).isEqualTo(initialCount + 3);
    }

    /**
     * TEST 5: Verify Metric Names Follow Prometheus Naming Convention
     * 
     * Source: Prometheus best practices
     * - Metric names should use underscores
     * - Should end with unit suffix (_total for counters, _seconds for timers)
     */
    @Test
    void shouldUseCorrectMetricNames_ForPrometheusExport() {
        // Act: Trigger metrics creation
        flightDataService.recordCacheHit();
        
        FlightData mockFlightData = FlightData.builder()
                .faFlightId("METRIC123-1234567890")
                .ident("METRIC123")
                .status("En-Route")
                .build();
        when(flightAwareClient.getFlightByIdent(anyString()))
                .thenReturn(Mono.just(mockFlightData));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(null);
        flightDataService.getFlightByIdentUncached("METRIC123");

        // Assert: Metrics exist with correct names (base names, Micrometer adds _total suffix for Prometheus)
        assertThat(meterRegistry.find("flight_cache_hits").counter()).isNotNull();
        assertThat(meterRegistry.find("flight_cache_misses").counter()).isNotNull();
        assertThat(meterRegistry.find("flightaware_api_duration").timer()).isNotNull();
    }
}

