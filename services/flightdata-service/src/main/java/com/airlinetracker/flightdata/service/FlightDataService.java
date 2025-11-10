package com.airlinetracker.flightdata.service;

import com.airlinetracker.flightdata.client.FlightAwareClient;
import com.airlinetracker.flightdata.dto.FlightData;
import lombok.RequiredArgsConstructor;
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
 * 
 * Cache Strategy (PRD FR-2):
 * - Cache key: "flights::{ident}"
 * - TTL: 5 minutes (300 seconds)
 * - Pattern: Cache-Aside (Lazy Loading)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightDataService {

    private final FlightAwareClient flightAwareClient;
    private final KafkaTemplate<String, FlightData> kafkaTemplate;

    @Value("${kafka.topic.flight-data-events:flight-data-events}")
    private String flightDataTopic;

    /**
     * Get flight data by ident
     * 
     * Flow (ARCHITECTURE.md Flow 1):
     * 1. Check cache (via @Cacheable annotation)
     * 2. If cache miss, call FlightAware API
     * 3. Store in cache (Spring handles this automatically)
     * 4. Publish event to Kafka
     * 5. Return flight data
     * 
     * @param ident Flight identifier (e.g., "UAL123")
     * @return FlightData object
     */
    @Cacheable(value = "flights", key = "#ident")
    public FlightData getFlightByIdent(String ident) {
        log.info("Cache miss for ident: {}. Fetching from FlightAware API...", ident);

        // Fetch from FlightAware API (blocking call)
        FlightData flightData = flightAwareClient.getFlightByIdent(ident).block();

        // Publish event to Kafka (async)
        publishFlightDataEvent(flightData);

        log.info("Flight data retrieved and cached: {} ({})", 
                flightData.getIdent(), flightData.getStatus());

        return flightData;
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

