package com.airlinetracker.flightdata.exception;

/**
 * Exception thrown when a flight cannot be found in FlightAware API
 */
public class FlightNotFoundException extends RuntimeException {
    
    public FlightNotFoundException(String ident) {
        super("Flight not found: " + ident);
    }
    
    public FlightNotFoundException(String ident, Throwable cause) {
        super("Flight not found: " + ident, cause);
    }
}

