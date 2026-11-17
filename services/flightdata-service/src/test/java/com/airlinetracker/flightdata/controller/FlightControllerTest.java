package com.airlinetracker.flightdata.controller;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.service.AmbiguousFlightException;
import com.airlinetracker.flightdata.service.FlightDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightController.class)
class FlightControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private FlightDataService flightDataService;

    @Test
    void candidateSearchReturnsEveryInstance() throws Exception {
        when(flightDataService.getFlightsByIdent("UAL123")).thenReturn(List.of(
                flight("UAL123-one", "En-Route / In Flight"), flight("UAL123-two", "Scheduled")));

        mockMvc.perform(get("/api/v1/flight/UAL123/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fa_flight_id").value("UAL123-one"))
                .andExpect(jsonPath("$[1].fa_flight_id").value("UAL123-two"));
    }

    @Test
    void legacyLookupExplainsAmbiguousFlightNumber() throws Exception {
        when(flightDataService.getFlightByIdent("UAL123"))
                .thenThrow(new AmbiguousFlightException("UAL123", 2));

        mockMvc.perform(get("/api/v1/flight/UAL123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Multiple flight instances"));
    }

    @Test
    void summaryRetryIsQueuedOnlyForAMatchingInstance() throws Exception {
        FlightData selected = flight("UAL123-one", "En-Route / In Flight");
        when(flightDataService.getFlightsByIdent("UAL123")).thenReturn(List.of(selected));

        mockMvc.perform(post("/api/v1/flight/UAL123/instance/UAL123-one/summary/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fa_flight_id").value("UAL123-one"));
        verify(flightDataService).requestSummary(selected);
    }

    private FlightData flight(String id, String status) {
        return FlightData.builder().faFlightId(id).ident("UAL123").status(status)
                .origin("KORD").destination("KLAX").dataSource("Demo fixture").build();
    }
}
