package com.airlinetracker.flightdata.controller;

import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.service.FlightDataService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Flight Data REST Controller
 * 
 * Source: API-SPEC.yml
 * Endpoint: GET /api/v1/flight/{ident}
 * 
 * Responsibilities:
 * - Expose REST API for flight data retrieval
 * - Input validation
 * - Exception handling (404, 500)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/flight")
@RequiredArgsConstructor
@Validated
public class FlightController {

    private final FlightDataService flightDataService;

    /**
     * Get flight data by ident
     * 
     * API Spec: GET /api/v1/flight/{ident}
     * 
     * @param ident Flight identifier (e.g., "UAL123")
     * @return FlightData with 200 OK
     * @throws FlightNotFoundException if flight not found (404)
     */
    @GetMapping("/{ident}")
    public ResponseEntity<FlightData> getFlightByIdent(
            @PathVariable @NotBlank(message = "Flight ident cannot be blank") String ident) {
        
        log.info("Received request for flight: {}", ident);

        FlightData flightData = flightDataService.getFlightByIdent(ident);

        log.info("Returning flight data for: {}", ident);
        return ResponseEntity.ok(flightData);
    }

    /**
     * Exception handler for FlightNotFoundException
     * Returns 404 with error message
     */
    @ExceptionHandler(FlightNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlightNotFound(FlightNotFoundException ex) {
        log.warn("Flight not found: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Exception handler for generic exceptions
     * Returns 500 with error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);
        
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Error response DTO
     */
    private record ErrorResponse(int status, String error, String message) {}
}

