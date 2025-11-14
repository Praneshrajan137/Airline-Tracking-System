package com.airlinetracker.flightdata.exception;

/**
 * Exception thrown when API rate limit is exceeded
 * Protects against API key misuse and cost overruns
 */
public class RateLimitExceededException extends RuntimeException {
    
    public RateLimitExceededException(String message) {
        super(message);
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

