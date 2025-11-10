package com.airlinetracker.llmsummary.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA Entity for flight_summaries table.
 * Matches ARCHITECTURE.md Section 5 - PostgreSQL Schema.
 */
@Entity
@Table(name = "flight_summaries", indexes = {
        @Index(name = "idx_ident", columnList = "ident")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ident", nullable = false, length = 50)
    private String ident;

    @Column(name = "fa_flight_id", nullable = false, unique = true, length = 100)
    private String faFlightId;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    /**
     * Set timestamps before persisting.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (generatedAt == null) {
            generatedAt = now;
        }
        if (lastUpdatedAt == null) {
            lastUpdatedAt = now;
        }
    }

    /**
     * Update last_updated_at timestamp before updating.
     */
    @PreUpdate
    protected void onUpdate() {
        lastUpdatedAt = Instant.now();
    }
}

