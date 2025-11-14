package com.airlinetracker.flightdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Flight Data DTO
 *
 * Source: docs/API-SPEC.yml - FlightData schema
 * Represents real-time flight information from FlightAware AeroAPI
 *
 * This DTO is used for:
 * - Response from FlightAware API
 * - Caching in Redis (must be Serializable)
 * - Publishing to Kafka
 * - REST API response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique FlightAware flight identifier
     * Format: {IDENT}-{TIMESTAMP}-airline-{ID}
     * Example: "UAL123-1678886400-airline-0123"
     */
    @NotBlank(message = "fa_flight_id is required")
    @JsonProperty("fa_flight_id")
    private String faFlightId;

    /**
     * Flight number (airline code + flight number)
     * Example: "UAL123"
     */
    @NotBlank(message = "ident is required")
    @JsonProperty("ident")
    private String ident;

    /**
     * Current flight status
     * Examples: "Scheduled", "En-Route / In Flight", "Landed", "Cancelled"
     */
    @NotNull(message = "status is required")
    @JsonProperty("status")
    private String status;

    /**
     * Scheduled departure time (ISO 8601)
     */
    @JsonProperty("scheduled_out")
    private Instant scheduledOut;

    /**
     * Actual departure time (ISO 8601, nullable if not yet departed)
     */
    @JsonProperty("actual_out")
    private Instant actualOut;

    /**
     * Scheduled arrival time (ISO 8601)
     */
    @JsonProperty("scheduled_in")
    private Instant scheduledIn;

    /**
     * Actual arrival time (ISO 8601, nullable if not yet arrived)
     */
    @JsonProperty("actual_in")
    private Instant actualIn;

    /**
     * Origin airport (FlightAware returns nested object)
     * We extract the ICAO code for simplicity
     */
    @JsonProperty("origin")
    @JsonDeserialize(using = AirportCodeDeserializer.class)
    private String origin;

    /**
     * Destination airport (FlightAware returns nested object)
     * We extract the ICAO code for simplicity
     */
    @JsonProperty("destination")
    @JsonDeserialize(using = AirportCodeDeserializer.class)
    private String destination;

    /**
     * Aircraft type code
     * Example: "B738" (Boeing 737-800)
     */
    @JsonProperty("aircraft_type")
    private String aircraftType;

    /**
     * Current latitude (decimal degrees, nullable if not in flight)
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Current longitude (decimal degrees, nullable if not in flight)
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * Current altitude in feet (nullable if not in flight)
     */
    @JsonProperty("altitude")
    private Integer altitude;

    /**
     * Current groundspeed in knots (nullable if not in flight)
     */
    @JsonProperty("groundspeed")
    private Integer groundspeed;
}
