package com.airlinetracker.llmsummary.controller;

import com.airlinetracker.llmsummary.dto.FlightSummaryResponse;
import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.service.SummaryService;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for flight summary retrieval.
 * Exposes GET /api/v1/flight/{ident}/summary endpoint.
 */
@RestController
@RequestMapping("/api/v1/flight")
@Validated
@Slf4j
public class SummaryController {

    private final SummaryService summaryService;

    // Regex pattern: 2-3 uppercase letters followed by 1-4 digits
    private static final String FLIGHT_IDENT_PATTERN = "^[A-Za-z]{2,3}\\d{1,4}$";

    @Autowired
    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /**
     * Get flight summary by ident.
     * 
     * @param ident Flight identifier (e.g., UAL123)
     * @return FlightSummaryResponse with summary text
     */
    @GetMapping("/{ident}/summary")
    public ResponseEntity<?> getSummary(
            @PathVariable 
            @Pattern(regexp = FLIGHT_IDENT_PATTERN, message = "Invalid flight ident format")
            String ident) {
        
        log.debug("Received request for flight summary: {}", ident);

        try {
            Optional<FlightSummary> summary = summaryService.getFlightSummaryByIdent(ident.toUpperCase());
            
            if (summary.isPresent()) {
                FlightSummaryResponse response = toResponse(summary.get());
                return ResponseEntity.ok(response);
            } else {
                log.warn("Summary not found for ident: {}", ident);
                Map<String, String> error = new HashMap<>();
                error.put("error", "Flight summary not found");
                error.put("ident", ident);
                return ResponseEntity.status(404).body(error);
            }

        } catch (Exception e) {
            log.error("Error retrieving summary for {}: {}", ident, e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * Convert FlightSummary entity to FlightSummaryResponse DTO.
     */
    private FlightSummaryResponse toResponse(FlightSummary summary) {
        return FlightSummaryResponse.builder()
                .ident(summary.getIdent())
                .faFlightId(summary.getFaFlightId())
                .summary(summary.getSummaryText())
                .generatedAt(summary.getGeneratedAt())
                .build();
    }

    /**
     * Global exception handler for validation errors.
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(
            jakarta.validation.ConstraintViolationException e) {
        
        log.warn("Validation error: {}", e.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", "Invalid flight identifier format");
        error.put("message", "Flight ident must be 2-3 letters followed by 1-4 digits (e.g., UAL123)");
        return ResponseEntity.status(400).body(error);
    }
}

