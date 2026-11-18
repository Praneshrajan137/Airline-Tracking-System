package com.airlinetracker.flightdata.controller;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.service.FlightDataService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FlightController.class)
@Import(FlightControllerTest.CacheTestConfiguration.class)
class FlightControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FlightDataService flightDataService;

    @TestConfiguration
    static class CacheTestConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("flight-candidates");
        }
    }

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
        when(flightDataService.getFlightsByIdent("UAL123")).thenReturn(List.of(
                flight("UAL123-one", "En-Route / In Flight"), flight("UAL123-two", "Scheduled")));

        mockMvc.perform(get("/api/v1/flight/UAL123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Multiple flight instances"));
    }

    @Test
    void legacyLookupUsesCachedCandidateSearchForSingleMatch() throws Exception {
        when(flightDataService.getFlightsByIdent("UAL123"))
                .thenReturn(List.of(flight("UAL123-one", "En-Route / In Flight")));

        mockMvc.perform(get("/api/v1/flight/UAL123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fa_flight_id").value("UAL123-one"));
        verify(flightDataService).getFlightsByIdent("UAL123");
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
