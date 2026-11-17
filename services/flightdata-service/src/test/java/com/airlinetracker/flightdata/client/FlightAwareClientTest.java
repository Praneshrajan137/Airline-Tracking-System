package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.RateLimitExceededException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightAwareClientTest {
    private WireMockServer wireMockServer;
    private FlightAwareClient client;
    private static final String API_KEY = "test-api-key-12345";
    private static final String IDENT = "UAL123";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
        client = new FlightAwareClient(WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port()).build(), API_KEY,
                new com.airlinetracker.flightdata.config.RateLimitConfig.RateLimiter(null, 100, 1_000, 10_000, false));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void returnsAllFlightInstancesAndAirportPositionMetadata() {
        stubFor(get(urlEqualTo("/flights/" + IDENT))
                .withHeader("x-apikey", equalTo(API_KEY))
                .willReturn(okJson("""
                        {"flights":[
                          {"fa_flight_id":"UAL123-one","ident":"UAL123","status":"En-Route / In Flight",
                           "origin":{"code":"KORD","name":"O'Hare","city":"Chicago","timezone":"America/Chicago"},
                           "destination":{"code":"KLAX","name":"Los Angeles International","city":"Los Angeles","timezone":"America/Los_Angeles"},
                           "last_position":{"latitude":39.8,"longitude":-98.6,"altitude":35000,"groundspeed":450,"timestamp":"2026-11-17T12:00:00Z"}},
                          {"fa_flight_id":"UAL123-two","ident":"UAL123","status":"Scheduled","origin":"KORD","destination":"KLAX"}
                        ],"num_pages":1}
                        """)));

        List<FlightData> result = client.getFlightsByIdent(IDENT).block();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFaFlightId()).isEqualTo("UAL123-one");
        assertThat(result.get(0).getOrigin()).isEqualTo("KORD");
        assertThat(result.get(0).getOriginCity()).isEqualTo("Chicago");
        assertThat(result.get(0).getDestinationTimezone()).isEqualTo("America/Los_Angeles");
        assertThat(result.get(0).getLatitude()).isEqualTo(39.8);
        assertThat(result.get(0).getLastPositionAt().toString()).isEqualTo("2026-11-17T12:00:00Z");
        assertThat(result.get(1).getOrigin()).isEqualTo("KORD");
        verify(getRequestedFor(urlEqualTo("/flights/" + IDENT)).withHeader("x-apikey", equalTo(API_KEY)));
    }

    @Test
    void mapsNotFoundWithoutTreatingProviderFailuresAsMissingFlights() {
        stubFor(get(urlEqualTo("/flights/INVALID999")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.getFlightsByIdent("INVALID999").block())
                .isInstanceOf(FlightNotFoundException.class);
    }

    @Test
    void mapsProviderThrottleToRateLimit() {
        stubFor(get(urlEqualTo("/flights/" + IDENT)).willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> client.getFlightsByIdent(IDENT).block())
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void mapsProviderServerFailureToServiceFailure() {
        stubFor(get(urlEqualTo("/flights/" + IDENT)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.getFlightsByIdent(IDENT).block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("503");
    }

    @Test
    void treatsAnEmptyCandidateListAsNotFound() {
        stubFor(get(urlEqualTo("/flights/" + IDENT)).willReturn(okJson("{\"flights\":[]}")));

        assertThatThrownBy(() -> client.getFlightsByIdent(IDENT).block())
                .isInstanceOf(FlightNotFoundException.class);
    }
}
