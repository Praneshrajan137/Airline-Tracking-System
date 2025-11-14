package com.airlinetracker.flightdata.service;

import com.airlinetracker.flightdata.client.FlightAwareClient;
import com.airlinetracker.flightdata.dto.FlightData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Flight Data Service
 * 
 * Source: ARCHITECTURE.md - flightdata-service responsibilities
 * 
 * Orchestrates:
 * 1. FlightAware API client (external data fetch)
 * 2. Redis caching (Cache-Aside pattern, TTL: 5 minutes)
 * 3. Kafka event publishing (flight-data-events topic)
 * 4. Custom metrics (Micrometer/Prometheus) - PRD NFR-4
 * 
 * Cache Strategy (PRD FR-2):
 * - Cache key: "flights::{ident}"
 * - TTL: 5 minutes (300 seconds)
 * - Pattern: Cache-Aside (Lazy Loading)
 * 
 * Custom Metrics (PRD NFR-4: Observability):
 * - flight_cache_hits_total: Counter for cache hits
 * - flight_cache_misses_total: Counter for cache misses
 * - flightaware_api_duration_seconds: Timer for API call duration
 */
@Slf4j
@Service
public class FlightDataService {

    private final FlightAwareClient flightAwareClient;
    private final KafkaTemplate<String, FlightData> kafkaTemplate;
    
    // Micrometer Metrics (PRD NFR-4)
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer apiDurationTimer;

    @Value("${kafka.topic.flight-data-events:flight-data-events}")
    private String flightDataTopic;

    /**
     * Constructor with dependency injection for metrics
     * 
     * @param flightAwareClient Client for FlightAware API
     * @param kafkaTemplate Kafka producer template
     * @param meterRegistry Micrometer registry for custom metrics
     */
    public FlightDataService(
            FlightAwareClient flightAwareClient,
            KafkaTemplate<String, FlightData> kafkaTemplate,
            MeterRegistry meterRegistry) {
        
        this.flightAwareClient = flightAwareClient;
        this.kafkaTemplate = kafkaTemplate;
        
        // Initialize custom metrics (PRD NFR-4)
        this.cacheHitCounter = Counter.builder("flight_cache_hits")
                .description("Number of flight data cache hits")
                .tag("service", "flightdata-service")
                .baseUnit("hits")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("flight_cache_misses")
                .description("Number of flight data cache misses")
                .tag("service", "flightdata-service")
                .baseUnit("misses")
                .register(meterRegistry);
        
        this.apiDurationTimer = Timer.builder("flightaware_api_duration")
                .description("Duration of FlightAware API calls")
                .tag("service", "flightdata-service")
                .register(meterRegistry);
        
        log.info("FlightDataService initialized with custom metrics");
    }

    /**
     * Get flight data by ident (with caching)
     * 
     * Flow (ARCHITECTURE.md Flow 1):
     * 1. Check cache (via @Cacheable annotation)
     * 2. If cache miss, call FlightAware API with metrics
     * 3. Store in cache (Spring handles this automatically)
     * 4. Publish event to Kafka
     * 5. Return flight data
     * 
     * Note: Cache hits cannot be detected here due to @Cacheable proxy behavior.
     * Cache hit metrics are recorded via AOP or manual tracking.
     * 
     * @param ident Flight identifier (e.g., "UAL123")
     * @return FlightData object
     */
    @Cacheable(value = "flights", key = "#ident")
    public FlightData getFlightByIdent(String ident) {
        log.info("Cache miss for ident: {}. Fetching from FlightAware API...", ident);
        
        // Record cache miss (PRD NFR-4)
        cacheMissCounter.increment();

        // Fetch from FlightAware API with duration tracking (PRD NFR-4)
        FlightData flightData = apiDurationTimer.record(() -> 
            flightAwareClient.getFlightByIdent(ident).block()
        );

        // Publish event to Kafka (async)
        publishFlightDataEvent(flightData);

        log.info("Flight data retrieved and cached: {} ({})", 
                flightData.getIdent(), flightData.getStatus());

        return flightData;
    }

    /**
     * Get flight data WITHOUT caching (for testing metrics)
     * 
     * This method is used by unit tests to verify metric recording
     * without the complexity of cache behavior.
     * 
     * @param ident Flight identifier
     * @return FlightData object
     */
    public FlightData getFlightByIdentUncached(String ident) {
        log.debug("Fetching flight data without cache: {}", ident);
        
        // Record cache miss
        cacheMissCounter.increment();
        
        // Fetch with timer
        FlightData flightData = apiDurationTimer.record(() -> 
            flightAwareClient.getFlightByIdent(ident).block()
        );
        
        // Publish event to Kafka
        publishFlightDataEvent(flightData);
        
        return flightData;
    }

    /**
     * Record cache hit metric
     * 
     * This method should be called when a cache hit is detected.
     * Can be invoked via AOP or manually in controller.
     * 
     * Source: PRD NFR-4 - Cache hit rate monitoring
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
        log.debug("Cache hit recorded");
    }

    /**
     * Publish flight-data-events event to Kafka
     * 
     * Event Schema (PRD.md Section 3.2):
     * - Topic: flight-data-events
     * - Key: fa_flight_id
     * - Value: FlightData JSON
     */
    private void publishFlightDataEvent(FlightData flightData) {
        try {
            kafkaTemplate.send(flightDataTopic, flightData.getFaFlightId(), flightData);
            log.debug("Published flight-data-events event for: {}", flightData.getIdent());
        } catch (Exception e) {
            // Log error but don't fail the request (Kafka is async)
            log.error("Failed to publish Kafka event for {}: {}", 
                    flightData.getIdent(), e.getMessage());
        }
    }
}

