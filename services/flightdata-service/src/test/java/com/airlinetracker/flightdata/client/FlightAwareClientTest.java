package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Test FlightAwareClient using WireMock
 * 
 * Test Requirements (from PRD FR-1):
 * - Call FlightAware AeroAPI /flights/{ident}
 * - Add x-apikey header from environment
 * - Handle 200 response with flight data
 * - Handle 404 for invalid ident
 * - Handle 500 server errors
 * - Timeout after 5 seconds
 */
class FlightAwareClientTest {

    private WireMockServer wireMockServer;
    private FlightAwareClient flightAwareClient;
    private static final String API_KEY = "test-api-key-12345";
    private static final String TEST_IDENT = "UAL123";

    @BeforeEach
    void setUp() {
        // Start WireMock server
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);

        // Create WebClient pointing to WireMock
        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:8089")
                .build();

        // Create mock rate limiter that always allows requests for testing
        com.airlinetracker.flightdata.config.RateLimitConfig.RateLimiter mockRateLimiter =
                new com.airlinetracker.flightdata.config.RateLimitConfig.RateLimiter(
                        null, 1000, 10000, 100000, false  // Disabled for tests
                );

        // Create client instance
        flightAwareClient = new FlightAwareClient(webClient, API_KEY, mockRateLimiter);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    /**
     * Test: Successful flight data retrieval (200 OK)
     */
    @Test
    void shouldReturnFlightData_WhenFlightExists() {
        // Arrange: Mock FlightAware API response
        String responseBody = """
                {
                    "fa_flight_id": "UAL123-1678886400-airline-0123",
                    "ident": "UAL123",
                    "status": "En-Route / In Flight",
                    "scheduled_out": "2023-03-15T12:00:00Z",
                    "actual_out": "2023-03-15T12:05:00Z",
                    "scheduled_in": "2023-03-15T18:30:00Z",
                    "actual_in": null,
                    "origin": "KORD",
                    "destination": "KLAX",
                    "aircraft_type": "B738",
                    "latitude": 39.8,
                    "longitude": -98.6,
                    "altitude": 35000,
                    "groundspeed": 450
                }
                """;

        stubFor(get(urlEqualTo("/flights/" + TEST_IDENT))
                .withHeader("x-apikey", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // Act
        FlightData result = flightAwareClient.getFlightByIdent(TEST_IDENT).block();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getFaFlightId()).isEqualTo("UAL123-1678886400-airline-0123");
        assertThat(result.getIdent()).isEqualTo("UAL123");
        assertThat(result.getStatus()).isEqualTo("En-Route / In Flight");
        assertThat(result.getOrigin()).isEqualTo("KORD");
        assertThat(result.getDestination()).isEqualTo("KLAX");
        assertThat(result.getAircraftType()).isEqualTo("B738");
        assertThat(result.getLatitude()).isEqualTo(39.8);
        assertThat(result.getLongitude()).isEqualTo(-98.6);
        assertThat(result.getAltitude()).isEqualTo(35000);
        assertThat(result.getGroundspeed()).isEqualTo(450);

        // Verify API was called with correct header
        verify(getRequestedFor(urlEqualTo("/flights/" + TEST_IDENT))
                .withHeader("x-apikey", equalTo(API_KEY)));
    }

    /**
     * Test: Flight not found (404)
     */
    @Test
    void shouldThrowFlightNotFoundException_When404() {
        // Arrange
        stubFor(get(urlEqualTo("/flights/INVALID999"))
                .withHeader("x-apikey", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("{\"error\": \"Flight not found\"}")));

        // Act & Assert
        assertThatThrownBy(() -> flightAwareClient.getFlightByIdent("INVALID999").block())
                .isInstanceOf(FlightNotFoundException.class)
                .hasMessageContaining("INVALID999");

        verify(getRequestedFor(urlEqualTo("/flights/INVALID999")));
    }

    /**
     * Test: Server error (500)
     */
    @Test
    void shouldThrowException_WhenServerError() {
        // Arrange
        stubFor(get(urlEqualTo("/flights/" + TEST_IDENT))
                .withHeader("x-apikey", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"error\": \"Internal server error\"}")));

        // Act & Assert
        assertThatThrownBy(() -> flightAwareClient.getFlightByIdent(TEST_IDENT).block())
                .isInstanceOf(RuntimeException.class);

        verify(getRequestedFor(urlEqualTo("/flights/" + TEST_IDENT)));
    }

    /**
     * Test: API key is included in request header
     */
    @Test
    void shouldIncludeApiKeyInHeader() {
        // Arrange
        stubFor(get(urlEqualTo("/flights/" + TEST_IDENT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"fa_flight_id\":\"test\",\"ident\":\"UAL123\",\"status\":\"test\"}")));

        // Act
        flightAwareClient.getFlightByIdent(TEST_IDENT).block();

        // Assert: Verify x-apikey header was sent
        verify(getRequestedFor(urlEqualTo("/flights/" + TEST_IDENT))
                .withHeader("x-apikey", equalTo(API_KEY)));
    }

    /**
     * Test: Timeout handling (5 seconds max)
     */
    @Test
    void shouldTimeout_WhenResponseTakesTooLong() {
        // Arrange: Simulate slow response (6 seconds delay, exceeds 5 second timeout)
        stubFor(get(urlEqualTo("/flights/" + TEST_IDENT))
                .withHeader("x-apikey", equalTo(API_KEY))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(6000)
                        .withBody("{\"fa_flight_id\":\"test\",\"ident\":\"UAL123\",\"status\":\"test\"}")));

        // Act & Assert: Should timeout
        assertThatThrownBy(() -> flightAwareClient.getFlightByIdent(TEST_IDENT).block())
                .isInstanceOf(RuntimeException.class);
    }
}

