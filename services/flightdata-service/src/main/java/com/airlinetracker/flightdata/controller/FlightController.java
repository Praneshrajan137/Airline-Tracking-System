package com.airlinetracker.flightdata.controller;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.RateLimitExceededException;
import com.airlinetracker.flightdata.exception.RateLimitUnavailableException;
import com.airlinetracker.flightdata.exception.SummaryEventUnavailableException;
import com.airlinetracker.flightdata.service.AmbiguousFlightException;
import com.airlinetracker.flightdata.service.FlightDataService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/flight")
@RequiredArgsConstructor
@Validated
public class FlightController {
    private static final String FLIGHT_IDENT_PATTERN = "^[A-Za-z]{2,3}\\d{1,4}$";
    private final FlightDataService flightDataService;

    /** Candidate search. A flight number may identify multiple dates or legs. */
    @GetMapping("/{ident}/candidates")
    public List<FlightData> getCandidates(
            @PathVariable @NotBlank @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident) {
        return flightDataService.getFlightsByIdent(ident.toUpperCase());
    }

    /** Retrieve one selected instance by its stable FlightAware ID. */
    @GetMapping("/{ident}/instance/{faFlightId}")
    public FlightData getFlightInstance(
            @PathVariable @NotBlank @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident,
            @PathVariable @NotBlank String faFlightId) {
        return flightDataService.getFlightsByIdent(ident.toUpperCase()).stream()
                .filter(flight -> faFlightId.equals(flight.getFaFlightId()))
                .findFirst()
                .orElseThrow(() -> new FlightNotFoundException(faFlightId));
    }

    /** Re-publish the selected instance if its asynchronous summary did not arrive. */
    @PostMapping("/{ident}/instance/{faFlightId}/summary/retry")
    public ResponseEntity<Map<String, String>> retrySummary(
            @PathVariable @NotBlank @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident,
            @PathVariable @NotBlank String faFlightId) {
        FlightData selected = flightDataService.getFlightsByIdent(ident.toUpperCase()).stream()
                .filter(flight -> faFlightId.equals(flight.getFaFlightId()))
                .findFirst()
                .orElseThrow(() -> new FlightNotFoundException(faFlightId));
        flightDataService.requestSummary(selected);
        return ResponseEntity.accepted().body(Map.of(
                "status", "queued", "fa_flight_id", selected.getFaFlightId()));
    }

    /** Legacy endpoint kept safe: it only succeeds when the ident has one match. */
    @GetMapping("/{ident}")
    public FlightData getFlightByIdent(
            @PathVariable @NotBlank @Pattern(regexp = FLIGHT_IDENT_PATTERN) String ident) {
        List<FlightData> flights = flightDataService.getFlightsByIdent(ident.toUpperCase());
        if (flights.size() != 1) {
            throw new AmbiguousFlightException(ident, flights.size());
        }
        return flights.get(0);
    }

    @ExceptionHandler(FlightNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlightNotFound(FlightNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(AmbiguousFlightException.class)
    public ResponseEntity<ErrorResponse> handleAmbiguousFlight(AmbiguousFlightException ex) {
        return error(HttpStatus.CONFLICT, "Multiple flight instances", ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "Provider limit reached", ex.getMessage());
    }

    @ExceptionHandler(RateLimitUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitStoreUnavailable(RateLimitUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Rate limiter unavailable",
                "Flight data is temporarily unavailable while request limits recover.");
    }

    @ExceptionHandler(SummaryEventUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSummaryUnavailable(SummaryEventUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Summary retry unavailable",
                "The summary request could not be queued. Please try again.");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                "Flight data is temporarily unavailable. Please retry shortly.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Flight request failed", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "The flight request could not be completed.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), error, message));
    }

    public record ErrorResponse(int status, String error, String message) {}
}
