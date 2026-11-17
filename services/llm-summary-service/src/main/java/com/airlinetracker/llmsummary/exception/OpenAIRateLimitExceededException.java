package com.airlinetracker.llmsummary.exception;

public class OpenAIRateLimitExceededException extends RuntimeException {
    public OpenAIRateLimitExceededException(String message) {
        super(message);
    }
}
