package com.airlinetracker.llmsummary.service;

import com.airlinetracker.llmsummary.client.OpenAIClient;
import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.repository.FlightSummaryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for processing flight data and managing summaries.
 * Orchestrates between OpenAI Client and PostgreSQL repository.
 */
@Service
@Slf4j
public class SummaryService {

    private final OpenAIClient openAIClient;
    private final FlightSummaryRepository repository;

    @Autowired
    public SummaryService(OpenAIClient openAIClient, FlightSummaryRepository repository) {
        this.openAIClient = openAIClient;
        this.repository = repository;
    }

    /**
     * Process flight data from Kafka: generate summary and save to database.
     * If summary already exists for this fa_flight_id, update it instead.
     * 
     * @param flightData Flight data from Kafka event
     */
    @Transactional
    public void processFlightData(FlightData flightData) {
        log.debug("Processing flight data for: {} ({})", flightData.getIdent(), flightData.getFaFlightId());

        try {
            // Generate summary using OpenAI
            String summaryText = openAIClient.generateSummary(flightData);
            log.info("Generated summary for flight {}: {}", flightData.getIdent(), summaryText);

            // Check if summary already exists for this fa_flight_id
            Optional<FlightSummary> existingSummary = repository.findByFaFlightId(flightData.getFaFlightId());

            FlightSummary flightSummary;
            if (existingSummary.isPresent()) {
                // Update existing summary
                flightSummary = existingSummary.get();
                flightSummary.setSummaryText(summaryText);
                log.debug("Updating existing summary for fa_flight_id: {}", flightData.getFaFlightId());
            } else {
                // Create new summary
                flightSummary = FlightSummary.builder()
                        .ident(flightData.getIdent())
                        .faFlightId(flightData.getFaFlightId())
                        .summaryText(summaryText)
                        .build();
                log.debug("Creating new summary for fa_flight_id: {}", flightData.getFaFlightId());
            }

            // Save to database (timestamps handled by @PrePersist/@PreUpdate)
            // Use saveAndFlush to ensure immediate DB commit for E2E test visibility
            repository.saveAndFlush(flightSummary);
            log.info("Successfully saved summary for flight {}", flightData.getIdent());

        } catch (Exception e) {
            log.error("Failed to process flight data for {}: {}", flightData.getIdent(), e.getMessage(), e);
            throw e; // Propagate to trigger Kafka retry if needed
        }
    }

    /**
     * Get the most recent summary for a flight by ident.
     * Used by REST API endpoint.
     * 
     * @param ident Flight identifier (e.g., UAL123)
     * @return Optional containing summary text if found
     */
    public Optional<String> getSummaryByIdent(String ident) {
        log.debug("Fetching summary for ident: {}", ident);

        return repository.findFirstByIdentOrderByGeneratedAtDesc(ident)
                .map(FlightSummary::getSummaryText);
    }

    /**
     * Get complete FlightSummary entity by ident (for API response).
     * 
     * @param ident Flight identifier
     * @return Optional containing FlightSummary if found
     */
    public Optional<FlightSummary> getFlightSummaryByIdent(String ident) {
        log.debug("Fetching complete FlightSummary for ident: {}", ident);

        return repository.findFirstByIdentOrderByGeneratedAtDesc(ident);
    }
}

