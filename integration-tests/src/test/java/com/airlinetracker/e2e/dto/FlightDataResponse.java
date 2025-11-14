package com.airlinetracker.e2e.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for FlightData API responses
 * Phase 5: E2E Integration Testing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightDataResponse {
    @JsonProperty("fa_flight_id")
    private String faFlightId;

    private String ident;
    private String status;

    @JsonProperty("scheduled_out")
    private Instant scheduledOut;

    @JsonProperty("actual_out")
    private Instant actualOut;

    @JsonProperty("scheduled_in")
    private Instant scheduledIn;

    @JsonProperty("actual_in")
    private Instant actualIn;

    private String origin;
    private String destination;

    @JsonProperty("aircraft_type")
    private String aircraftType;

    private Double latitude;
    private Double longitude;
    private Integer altitude;
    private Integer groundspeed;
}


