package com.airlinetracker.llmsummary.controller;

import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.service.SummaryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD Test for SummaryController.
 * Tests REST API endpoint for retrieving flight summaries.
 *
 * RED Phase: This test will fail until SummaryController is implemented.
 */
@WebMvcTest(SummaryController.class)
@DisplayName("SummaryController Tests")
class SummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

    @Test
    @DisplayName("Should return 200 with summary when flight exists")
    void testGetSummary_Success() throws Exception {
        // Given: Flight summary exists
        FlightSummary summary = FlightSummary.builder()
                .id(1L)
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .summaryText("United Flight 123 is en route from Chicago to Los Angeles.")
                .generatedAt(Instant.parse("2025-11-10T10:00:00Z"))
                .lastUpdatedAt(Instant.parse("2025-11-10T10:00:00Z"))
                .build();

        when(summaryService.getFlightSummariesByIdent("UAL123"))
                .thenReturn(List.of(summary));

        // When/Then: GET /api/v1/flight/UAL123/summary
        mockMvc.perform(get("/api/v1/flight/UAL123/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.ident").value("UAL123"))
                .andExpect(jsonPath("$.fa_flight_id").value("UAL123-1234567890-1-0"))
                .andExpect(jsonPath("$.summary_text").value("United Flight 123 is en route from Chicago to Los Angeles."))
                .andExpect(jsonPath("$.generated_at").value("2025-11-10T10:00:00Z"));
    }

    @Test
    @DisplayName("Should return 404 when flight summary not found")
    void testGetSummary_NotFound() throws Exception {
        // Given: No summary exists (using valid format but non-existent flight)
        when(summaryService.getFlightSummariesByIdent("XY999"))
                .thenReturn(List.of());

        // When/Then: Should return 404
        mockMvc.perform(get("/api/v1/flight/XY999/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Summary not ready"));
    }

    @Test
    @DisplayName("Should validate ident format")
    void testGetSummary_InvalidIdent() throws Exception {
        // When/Then: Invalid ident format should return 400
        mockMvc.perform(get("/api/v1/flight/INVALID@123/summary"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle service errors")
    void testGetSummary_ServiceError() throws Exception {
        // Given: Service throws exception
        when(summaryService.getFlightSummariesByIdent("UAL123"))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then: Should return 500
        mockMvc.perform(get("/api/v1/flight/UAL123/summary"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should accept valid flight idents with 2-3 letter codes")
    void testGetSummary_ValidIdentFormats() throws Exception {
        // Given: Various valid ident formats
        FlightSummary summary = FlightSummary.builder()
                .id(1L)
                .ident("BA456")
                .faFlightId("BA456-123-1-0")
                .summaryText("Summary")
                .generatedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();

        // Test 2-letter airline code
        when(summaryService.getFlightSummariesByIdent("BA456"))
                .thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/flight/BA456/summary"))
                .andExpect(status().isOk());

        // Test 3-letter airline code
        summary.setIdent("UAL123");
        when(summaryService.getFlightSummariesByIdent("UAL123"))
                .thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/flight/UAL123/summary"))
                .andExpect(status().isOk());

        // Test 1-digit flight number
        summary.setIdent("AA5");
        when(summaryService.getFlightSummariesByIdent("AA5"))
                .thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/flight/AA5/summary"))
                .andExpect(status().isOk());

        // Test 4-digit flight number
        summary.setIdent("AA1234");
        when(summaryService.getFlightSummariesByIdent("AA1234"))
                .thenReturn(List.of(summary));
        mockMvc.perform(get("/api/v1/flight/AA1234/summary"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Summary lookup is pinned to the selected flight instance")
    void testGetSummary_ByFlightInstance() throws Exception {
        FlightSummary summary = FlightSummary.builder()
                .ident("UAL123")
                .faFlightId("UAL123-20261117-airline-1")
                .summaryText("The selected instance is en route.")
                .generatedAt(Instant.parse("2026-11-17T10:00:00Z"))
                .lastUpdatedAt(Instant.parse("2026-11-17T10:00:00Z"))
                .build();
        when(summaryService.getFlightSummaryByFaFlightId("UAL123-20261117-airline-1"))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/flight/UAL123/summary/UAL123-20261117-airline-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fa_flight_id").value("UAL123-20261117-airline-1"));
    }

    @Test
    @DisplayName("An exact flight instance without a summary is not ready yet")
    void testGetSummary_ByFlightInstance_NotReady() throws Exception {
        when(summaryService.getFlightSummaryByFaFlightId("UAL123-20261117-airline-1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/flight/UAL123/summary/UAL123-20261117-airline-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Summary not ready"));
    }

    @Test
    @DisplayName("An exact flight instance cannot return another flight's summary")
    void testGetSummary_ByFlightInstance_MismatchedIdent() throws Exception {
        FlightSummary summary = FlightSummary.builder()
                .ident("DAL456")
                .faFlightId("DAL456-20261117-airline-1")
                .build();
        when(summaryService.getFlightSummaryByFaFlightId("DAL456-20261117-airline-1"))
                .thenReturn(Optional.of(summary));

        mockMvc.perform(get("/api/v1/flight/UAL123/summary/DAL456-20261117-airline-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Summary not ready"));
    }

    @Test
    @DisplayName("Legacy summary lookup rejects ambiguous flight numbers")
    void testGetSummary_LegacyAmbiguous() throws Exception {
        FlightSummary first = FlightSummary.builder().ident("UAL123").faFlightId("UAL123-one").build();
        FlightSummary second = FlightSummary.builder().ident("UAL123").faFlightId("UAL123-two").build();
        when(summaryService.getFlightSummariesByIdent("UAL123")).thenReturn(List.of(first, second));

        mockMvc.perform(get("/api/v1/flight/UAL123/summary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Flight instance required"));
    }
}
