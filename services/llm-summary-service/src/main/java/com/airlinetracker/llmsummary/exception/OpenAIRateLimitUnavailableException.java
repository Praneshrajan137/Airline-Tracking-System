package com.airlinetracker.llmsummary.exception;

public class OpenAIRateLimitUnavailableException extends RuntimeException {
    public OpenAIRateLimitUnavailableException(String message) {
        super(message);
    }

    public OpenAIRateLimitUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
