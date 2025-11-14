package com.airlinetracker.llmsummary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for FlightSummary API response.
 * Matches API-SPEC.yml FlightSummary schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummaryResponse {

    @JsonProperty("ident")
    private String ident;

    @JsonProperty("fa_flight_id")
    private String faFlightId;

    @JsonProperty("summary_text")
    private String summaryText;

    @JsonProperty("generated_at")
    private Instant generatedAt;
}

