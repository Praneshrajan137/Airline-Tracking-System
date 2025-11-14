package com.airlinetracker.llmsummary.service;

import com.airlinetracker.llmsummary.client.OpenAIClient;
import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.repository.FlightSummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TDD Test for SummaryService.
 * Tests the orchestration between OpenAI Client and Repository.
 *
 * RED Phase: This test will fail until SummaryService is implemented.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SummaryService Tests")
class SummaryServiceTest {

    @Mock
    private OpenAIClient openAIClient;

    @Mock
    private FlightSummaryRepository repository;

    @InjectMocks
    private SummaryService summaryService;

    private FlightData testFlightData;

    @BeforeEach
    void setUp() {
        testFlightData = FlightData.builder()
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .status("En-Route / In Flight")
                .scheduledOut(Instant.parse("2025-11-10T09:00:00Z"))
                .actualOut(Instant.parse("2025-11-10T10:00:00Z"))
                .scheduledIn(Instant.parse("2025-11-10T14:00:00Z"))
                .actualIn(null)
                .origin("KORD")
                .destination("KLAX")
                .aircraftType("B738")
                .latitude(39.8283)
                .longitude(-98.5795)
                .altitude(35000)
                .groundspeed(450)
                .build();
    }

    @Test
    @DisplayName("Should process flight data and generate summary")
    void testProcessFlightData_Success() {
        // Given: Mock OpenAI response and repository save
        String expectedSummary = "United Flight 123 is en route from Chicago to Los Angeles.";
        when(openAIClient.generateSummary(any(FlightData.class))).thenReturn(expectedSummary);
        when(repository.saveAndFlush(any(FlightSummary.class))).thenAnswer(invocation -> {
            FlightSummary summary = invocation.getArgument(0);
            summary.setId(1L);
            return summary;
        });

        // When: Processing flight data
        summaryService.processFlightData(testFlightData);

        // Then: Verify OpenAI client was called
        verify(openAIClient, times(1)).generateSummary(testFlightData);

        // Verify repository save was called with correct data
        ArgumentCaptor<FlightSummary> captor = ArgumentCaptor.forClass(FlightSummary.class);
        verify(repository, times(1)).saveAndFlush(captor.capture());

        FlightSummary savedSummary = captor.getValue();
        assertThat(savedSummary.getIdent()).isEqualTo("UAL123");
        assertThat(savedSummary.getFaFlightId()).isEqualTo("UAL123-1234567890-1-0");
        assertThat(savedSummary.getSummaryText()).isEqualTo(expectedSummary);
    }

    @Test
    @DisplayName("Should update existing summary if fa_flight_id already exists")
    void testProcessFlightData_UpdateExisting() {
        // Given: Existing summary in database
        FlightSummary existingSummary = FlightSummary.builder()
                .id(1L)
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .summaryText("Old summary")
                .generatedAt(Instant.now().minusSeconds(3600))
                .lastUpdatedAt(Instant.now().minusSeconds(3600))
                .build();

        String newSummary = "Updated summary for Flight 123.";

        when(repository.findByFaFlightId("UAL123-1234567890-1-0")).thenReturn(Optional.of(existingSummary));
        when(openAIClient.generateSummary(any(FlightData.class))).thenReturn(newSummary);
        when(repository.saveAndFlush(any(FlightSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing the same flight data
        summaryService.processFlightData(testFlightData);

        // Then: Verify existing summary was updated, not created
        verify(repository, times(1)).findByFaFlightId("UAL123-1234567890-1-0");

        ArgumentCaptor<FlightSummary> captor = ArgumentCaptor.forClass(FlightSummary.class);
        verify(repository, times(1)).saveAndFlush(captor.capture());

        FlightSummary updatedSummary = captor.getValue();
        assertThat(updatedSummary.getId()).isEqualTo(1L); // Same ID
        assertThat(updatedSummary.getSummaryText()).isEqualTo(newSummary); // Updated text
    }

    @Test
    @DisplayName("Should get latest summary by ident")
    void testGetSummaryByIdent_Success() {
        // Given: Summary exists in database
        FlightSummary existingSummary = FlightSummary.builder()
                .id(1L)
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .summaryText("Flight summary")
                .generatedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();

        when(repository.findFirstByIdentOrderByGeneratedAtDesc("UAL123"))
                .thenReturn(Optional.of(existingSummary));

        // When: Getting summary by ident
        Optional<String> result = summaryService.getSummaryByIdent("UAL123");

        // Then: Should return the summary text
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("Flight summary");
        verify(repository, times(1)).findFirstByIdentOrderByGeneratedAtDesc("UAL123");
    }

    @Test
    @DisplayName("Should return empty when summary not found")
    void testGetSummaryByIdent_NotFound() {
        // Given: No summary in database
        when(repository.findFirstByIdentOrderByGeneratedAtDesc("NONEXISTENT"))
                .thenReturn(Optional.empty());

        // When: Getting summary for non-existent ident
        Optional<String> result = summaryService.getSummaryByIdent("NONEXISTENT");

        // Then: Should return empty
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle OpenAI client errors gracefully")
    void testProcessFlightData_OpenAIError() {
        // Given: OpenAI client throws exception
        when(openAIClient.generateSummary(any(FlightData.class)))
                .thenThrow(new RuntimeException("OpenAI API failed"));

        // When/Then: Should propagate exception (or handle based on requirements)
        try {
            summaryService.processFlightData(testFlightData);
            // If we implement error handling, we'd verify it here
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("OpenAI API failed");
        }

        // Verify repository was NOT called when OpenAI fails
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should call repository with correct fa_flight_id")
    void testProcessFlightData_VerifyFaFlightId() {
        // Given: Mock responses
        when(openAIClient.generateSummary(any(FlightData.class)))
                .thenReturn("Summary");
        when(repository.findByFaFlightId(anyString())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(FlightSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing flight data
        summaryService.processFlightData(testFlightData);

        // Then: Verify correct fa_flight_id was queried
        verify(repository, times(1)).findByFaFlightId("UAL123-1234567890-1-0");
    }
}
