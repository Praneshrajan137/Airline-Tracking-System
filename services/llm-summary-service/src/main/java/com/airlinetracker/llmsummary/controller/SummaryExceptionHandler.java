package com.airlinetracker.llmsummary.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class SummaryExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid flight identifier",
                "message", "Use 2–3 letters followed by 1–4 digits."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        log.error("Summary request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Summary service unavailable",
                "message", "The summary could not be retrieved. Please retry."));
    }
}
