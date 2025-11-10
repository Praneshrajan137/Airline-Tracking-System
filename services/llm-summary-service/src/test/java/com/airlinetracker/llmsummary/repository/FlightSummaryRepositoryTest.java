package com.airlinetracker.llmsummary.repository;

import com.airlinetracker.llmsummary.entity.FlightSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD Test for FlightSummaryRepository.
 * Tests all repository methods defined in ARCHITECTURE.md.
 * 
 * RED Phase: This test will fail until the entity and repository are fully configured.
 */
@DataJpaTest
@DisplayName("FlightSummaryRepository Tests")
class FlightSummaryRepositoryTest {

    @Autowired
    private FlightSummaryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private FlightSummary testSummary;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        repository.deleteAll();

        // Create test data
        testSummary = FlightSummary.builder()
                .ident("UAL123")
                .faFlightId("UAL123-1234567890-1-0")
                .summaryText("United Flight 123 is en route from Chicago to Los Angeles.")
                .generatedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should save and retrieve flight summary")
    void testSaveAndRetrieve() {
        // Given: A flight summary
        // When: Saving to database
        FlightSummary saved = repository.save(testSummary);
        entityManager.flush();

        // Then: Should have generated ID and be retrievable
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getIdent()).isEqualTo("UAL123");
        assertThat(saved.getFaFlightId()).isEqualTo("UAL123-1234567890-1-0");
        assertThat(saved.getSummaryText()).contains("United Flight 123");

        // Verify we can retrieve it
        Optional<FlightSummary> retrieved = repository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getIdent()).isEqualTo("UAL123");
    }

    @Test
    @DisplayName("Should find flight summary by fa_flight_id")
    void testFindByFaFlightId() {
        // Given: A saved flight summary
        repository.save(testSummary);
        entityManager.flush();

        // When: Searching by fa_flight_id
        Optional<FlightSummary> found = repository.findByFaFlightId("UAL123-1234567890-1-0");

        // Then: Should find the summary
        assertThat(found).isPresent();
        assertThat(found.get().getIdent()).isEqualTo("UAL123");
        assertThat(found.get().getFaFlightId()).isEqualTo("UAL123-1234567890-1-0");
    }

    @Test
    @DisplayName("Should return empty when fa_flight_id not found")
    void testFindByFaFlightIdNotFound() {
        // When: Searching for non-existent fa_flight_id
        Optional<FlightSummary> found = repository.findByFaFlightId("NONEXISTENT");

        // Then: Should return empty
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find most recent summary by ident")
    void testFindFirstByIdentOrderByGeneratedAtDesc() {
        // Given: Multiple summaries for same ident
        FlightSummary older = FlightSummary.builder()
                .ident("UAL123")
                .faFlightId("UAL123-OLD")
                .summaryText("Old summary")
                .generatedAt(Instant.now().minusSeconds(3600))
                .lastUpdatedAt(Instant.now().minusSeconds(3600))
                .build();

        FlightSummary newer = FlightSummary.builder()
                .ident("UAL123")
                .faFlightId("UAL123-NEW")
                .summaryText("New summary")
                .generatedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();

        repository.save(older);
        repository.save(newer);
        entityManager.flush();

        // When: Finding most recent by ident
        Optional<FlightSummary> found = repository.findFirstByIdentOrderByGeneratedAtDesc("UAL123");

        // Then: Should return the newer summary
        assertThat(found).isPresent();
        assertThat(found.get().getFaFlightId()).isEqualTo("UAL123-NEW");
        assertThat(found.get().getSummaryText()).isEqualTo("New summary");
    }

    @Test
    @DisplayName("Should check if summary exists by fa_flight_id")
    void testExistsByFaFlightId() {
        // Given: A saved flight summary
        repository.save(testSummary);
        entityManager.flush();

        // When: Checking existence
        boolean exists = repository.existsByFaFlightId("UAL123-1234567890-1-0");
        boolean notExists = repository.existsByFaFlightId("NONEXISTENT");

        // Then: Should return correct existence status
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should enforce unique constraint on fa_flight_id")
    void testUniqueConstraintOnFaFlightId() {
        // Given: A saved flight summary
        repository.save(testSummary);
        entityManager.flush();

        // When: Attempting to save another summary with same fa_flight_id
        FlightSummary duplicate = FlightSummary.builder()
                .ident("UAL456")
                .faFlightId("UAL123-1234567890-1-0") // Same as testSummary
                .summaryText("Duplicate")
                .generatedAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .build();

        // Then: Should throw exception
        try {
            repository.save(duplicate);
            entityManager.flush();
            throw new AssertionError("Expected exception for duplicate fa_flight_id");
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).containsAnyOf("constraint", "unique", "duplicate");
        }
    }

    @Test
    @DisplayName("Should automatically set timestamps on create")
    void testTimestampsOnCreate() {
        // Given: Summary without timestamps
        FlightSummary newSummary = FlightSummary.builder()
                .ident("BA456")
                .faFlightId("BA456-9876543210-1-0")
                .summaryText("British Airways flight")
                .build();

        // When: Saving to database
        FlightSummary saved = repository.save(newSummary);
        entityManager.flush();

        // Then: Timestamps should be set automatically
        assertThat(saved.getGeneratedAt()).isNotNull();
        assertThat(saved.getLastUpdatedAt()).isNotNull();
        assertThat(saved.getGeneratedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(saved.getLastUpdatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should update last_updated_at on update")
    void testTimestampOnUpdate() throws InterruptedException {
        // Given: A saved flight summary
        FlightSummary saved = repository.save(testSummary);
        entityManager.flush();
        Instant originalUpdatedAt = saved.getLastUpdatedAt();

        // When: Updating the summary
        Thread.sleep(100); // Small delay to ensure timestamp difference
        saved.setSummaryText("Updated summary text");
        FlightSummary updated = repository.save(saved);
        entityManager.flush();

        // Then: last_updated_at should be updated
        assertThat(updated.getLastUpdatedAt()).isAfter(originalUpdatedAt);
        assertThat(updated.getGeneratedAt()).isEqualTo(saved.getGeneratedAt()); // generated_at should not change
    }
}

