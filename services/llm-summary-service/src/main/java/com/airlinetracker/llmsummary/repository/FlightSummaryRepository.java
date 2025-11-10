package com.airlinetracker.llmsummary.repository;

import com.airlinetracker.llmsummary.entity.FlightSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for FlightSummary entity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface FlightSummaryRepository extends JpaRepository<FlightSummary, Long> {

    /**
     * Find flight summary by fa_flight_id.
     * @param faFlightId FlightAware flight ID (unique identifier)
     * @return Optional containing FlightSummary if found
     */
    Optional<FlightSummary> findByFaFlightId(String faFlightId);

    /**
     * Find the most recent flight summary by ident.
     * Useful when multiple flights with same ident exist.
     * @param ident Flight identifier (e.g., UAL123)
     * @return Optional containing the most recent FlightSummary
     */
    Optional<FlightSummary> findFirstByIdentOrderByGeneratedAtDesc(String ident);

    /**
     * Check if a summary exists for a given fa_flight_id.
     * @param faFlightId FlightAware flight ID
     * @return true if summary exists, false otherwise
     */
    boolean existsByFaFlightId(String faFlightId);
}

