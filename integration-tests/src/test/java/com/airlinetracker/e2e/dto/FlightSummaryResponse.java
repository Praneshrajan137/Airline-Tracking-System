package com.airlinetracker.e2e.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for FlightSummary API responses
 * Phase 5: E2E Integration Testing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummaryResponse {
    private Long id;
    private String ident;

    @JsonProperty("fa_flight_id")
    private String faFlightId;

    @JsonProperty("summary_text")
    private String summaryText;

    @JsonProperty("generated_at")
    private Instant generatedAt;
}


