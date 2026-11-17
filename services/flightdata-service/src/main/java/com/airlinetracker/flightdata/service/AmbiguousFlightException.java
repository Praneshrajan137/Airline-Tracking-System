package com.airlinetracker.flightdata.service;

/** Raised when a flight number matches more than one dated flight instance. */
public class AmbiguousFlightException extends RuntimeException {
    private final int candidateCount;

    public AmbiguousFlightException(String ident, int candidateCount) {
        super("More than one flight instance matches " + ident + "; select a dated flight instance.");
        this.candidateCount = candidateCount;
    }

    public int getCandidateCount() {
        return candidateCount;
    }
}
