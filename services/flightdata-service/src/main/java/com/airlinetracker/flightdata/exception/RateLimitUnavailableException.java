package com.airlinetracker.flightdata.exception;

/** Limiter storage failed; outbound provider requests must be denied until it recovers. */
public class RateLimitUnavailableException extends RuntimeException {
    public RateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RateLimitUnavailableException(String message) {
        super(message);
    }
}
