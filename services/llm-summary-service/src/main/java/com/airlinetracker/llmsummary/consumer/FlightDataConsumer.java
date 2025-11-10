package com.airlinetracker.llmsummary.consumer;

import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.service.SummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for flight-data-events.
 * Listens to Kafka topic and triggers summary generation.
 * 
 * Source: PRD.md Section 3.2 - Kafka Event Schema
 */
@Component
@Slf4j
public class FlightDataConsumer {

    private final SummaryService summaryService;

    @Autowired
    public FlightDataConsumer(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * Consume flight data events from Kafka.
     * Topic: flight-data-events (PRD.md line 290)
     * 
     * @param flightData Deserialized flight data from Kafka event
     */
    @KafkaListener(
            topics = "${kafka.topics.flight-data-events:flight-data-events}",
            groupId = "${spring.kafka.consumer.group-id:llm-summary-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeFlightData(FlightData flightData) {
        log.info("Received flight data event: {} ({})", flightData.getIdent(), flightData.getFaFlightId());

        try {
            // Delegate to service layer for processing
            summaryService.processFlightData(flightData);
            log.info("Successfully processed flight data event for {}", flightData.getIdent());

        } catch (Exception e) {
            log.error("Failed to process flight data event for {}: {}", 
                    flightData.getIdent(), e.getMessage(), e);
            // Kafka will handle retry based on configuration
            throw e;
        }
    }
}

