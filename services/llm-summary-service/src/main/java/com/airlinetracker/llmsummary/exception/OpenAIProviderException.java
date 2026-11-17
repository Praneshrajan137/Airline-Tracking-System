package com.airlinetracker.llmsummary.exception;

import java.time.Duration;

/** Provider HTTP failure without retaining or logging a response body. */
public class OpenAIProviderException extends RuntimeException {
    private final int statusCode;
    private final Duration retryAfter;

    public OpenAIProviderException(int statusCode, Duration retryAfter) {
        super("OpenAI returned HTTP " + statusCode);
        this.statusCode = statusCode;
        this.retryAfter = retryAfter;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
