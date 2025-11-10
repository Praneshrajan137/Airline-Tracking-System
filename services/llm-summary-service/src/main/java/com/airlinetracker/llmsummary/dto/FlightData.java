package com.airlinetracker.llmsummary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for FlightData consumed from Kafka.
 * Matches API-SPEC.yml FlightData schema and flightdata-service output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightData {

    @JsonProperty("ident")
    private String ident;

    @JsonProperty("fa_flight_id")
    private String faFlightId;

    @JsonProperty("actual_off")
    private Instant actualOff;

    @JsonProperty("actual_on")
    private Instant actualOn;

    @JsonProperty("origin")
    private Airport origin;

    @JsonProperty("destination")
    private Airport destination;

    @JsonProperty("last_position")
    private Position lastPosition;

    @JsonProperty("aircraft_type")
    private String aircraftType;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Airport {
        @JsonProperty("code")
        private String code;

        @JsonProperty("code_icao")
        private String codeIcao;

        @JsonProperty("code_iata")
        private String codeIata;

        @JsonProperty("code_lid")
        private String codeLid;

        @JsonProperty("timezone")
        private String timezone;

        @JsonProperty("name")
        private String name;

        @JsonProperty("city")
        private String city;

        @JsonProperty("airport_info_url")
        private String airportInfoUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Position {
        @JsonProperty("fa_flight_id")
        private String faFlightId;

        @JsonProperty("altitude")
        private Integer altitude;

        @JsonProperty("altitude_change")
        private String altitudeChange;

        @JsonProperty("groundspeed")
        private Integer groundspeed;

        @JsonProperty("heading")
        private Integer heading;

        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("update_type")
        private String updateType;
    }
}

