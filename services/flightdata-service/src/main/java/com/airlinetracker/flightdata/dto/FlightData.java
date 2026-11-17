package com.airlinetracker.flightdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/** Flight instance data returned by AeroAPI or the explicitly labeled demo fixture. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlightData implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank
    @JsonProperty("fa_flight_id")
    private String faFlightId;

    @NotBlank
    @JsonProperty("ident")
    private String ident;

    @NotNull
    @JsonProperty("status")
    private String status;

    @JsonProperty("scheduled_out")
    private Instant scheduledOut;
    @JsonProperty("actual_out")
    private Instant actualOut;
    @JsonProperty("scheduled_in")
    private Instant scheduledIn;
    @JsonProperty("actual_in")
    private Instant actualIn;

    @JsonProperty("origin")
    private String origin;
    @JsonProperty("destination")
    private String destination;
    @JsonProperty("origin_name")
    private String originName;
    @JsonProperty("origin_city")
    private String originCity;
    @JsonProperty("origin_timezone")
    private String originTimezone;
    @JsonProperty("destination_name")
    private String destinationName;
    @JsonProperty("destination_city")
    private String destinationCity;
    @JsonProperty("destination_timezone")
    private String destinationTimezone;

    @JsonProperty("aircraft_type")
    private String aircraftType;
    @JsonProperty("latitude")
    private Double latitude;
    @JsonProperty("longitude")
    private Double longitude;
    @JsonProperty("altitude")
    private Integer altitude;
    @JsonProperty("groundspeed")
    private Integer groundspeed;
    @JsonProperty("last_position_at")
    private Instant lastPositionAt;
    /** Time this service fetched or served the source record. */
    @JsonProperty("checked_at")
    private Instant checkedAt;
    @JsonProperty("data_source")
    private String dataSource;

}
