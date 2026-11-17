package com.airlinetracker.llmsummary.controller;

import com.airlinetracker.llmsummary.dto.FlightSummaryResponse;
import com.airlinetracker.llmsummary.entity.FlightSummary;
import com.airlinetracker.llmsummary.service.SummaryService;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/flight")
@Validated
@Slf4j
public class SummaryController {
    private static final String FLIGHT_IDENT_PATTERN = "^[A-Za-z]{2,3}\\d{1,4}$";
    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** Exact lookup used by the client after a traveler selects a dated instance. */
    @GetMapping("/{ident}/summary/{faFlightId}")
    public ResponseEntity<?> getSummaryForFlight(
            @PathVariable @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident,
            @PathVariable String faFlightId) {
        Optional<FlightSummary> summary = summaryService.getFlightSummaryByFaFlightId(faFlightId);
        if (summary.isEmpty() || !summary.get().getIdent().equalsIgnoreCase(ident)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Summary not ready", "message", "A summary for this flight instance is not available yet."));
        }
        return ResponseEntity.ok(toResponse(summary.get()));
    }

    /** Legacy lookup succeeds only when there is exactly one known instance. */
    @GetMapping("/{ident}/summary")
    public ResponseEntity<?> getSummary(@PathVariable @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident) {
        List<FlightSummary> summaries = summaryService.getFlightSummariesByIdent(ident.toUpperCase());
        if (summaries.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Summary not ready", "message", "A summary for this flight is not available yet."));
        }
        if (summaries.size() > 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Flight instance required", "message", "Select a dated flight instance to retrieve its summary."));
        }
        return ResponseEntity.ok(toResponse(summaries.get(0)));
    }

    private FlightSummaryResponse toResponse(FlightSummary summary) {
        return FlightSummaryResponse.builder()
                .ident(summary.getIdent())
                .faFlightId(summary.getFaFlightId())
                .summaryText(summary.getSummaryText())
                .generatedAt(summary.getGeneratedAt())
                .build();
    }
}
