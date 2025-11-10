-- Initialize PostgreSQL database for Airline Tracker System
-- This script runs automatically when the PostgreSQL container starts for the first time

-- Create flight_summaries table
CREATE TABLE IF NOT EXISTS flight_summaries (
    id SERIAL PRIMARY KEY,
    flight_ident VARCHAR(7) NOT NULL,
    fa_flight_id VARCHAR(100) NOT NULL UNIQUE,
    summary_text TEXT NOT NULL,
    model_used VARCHAR(50) NOT NULL DEFAULT 'gpt-3.5-turbo',
    tokens_used INTEGER,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_flight_summaries_flight_ident ON flight_summaries(flight_ident);
CREATE INDEX IF NOT EXISTS idx_flight_summaries_generated_at ON flight_summaries(generated_at);
CREATE INDEX IF NOT EXISTS idx_flight_summaries_fa_flight_id ON flight_summaries(fa_flight_id);

-- Add table and column comments
COMMENT ON TABLE flight_summaries IS 'Stores AI-generated flight summaries from OpenAI API';
COMMENT ON COLUMN flight_summaries.id IS 'Primary key (auto-increment)';
COMMENT ON COLUMN flight_summaries.flight_ident IS 'Flight identifier (e.g., UAL123)';
COMMENT ON COLUMN flight_summaries.fa_flight_id IS 'Unique FlightAware flight ID';
COMMENT ON COLUMN flight_summaries.summary_text IS 'AI-generated natural language summary';
COMMENT ON COLUMN flight_summaries.model_used IS 'OpenAI model used for generation (e.g., gpt-3.5-turbo)';
COMMENT ON COLUMN flight_summaries.tokens_used IS 'Number of OpenAI tokens consumed';
COMMENT ON COLUMN flight_summaries.generated_at IS 'Timestamp when summary was generated';
COMMENT ON COLUMN flight_summaries.created_at IS 'Record creation timestamp';
COMMENT ON COLUMN flight_summaries.updated_at IS 'Last update timestamp';

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE flight_summaries TO airline_tracker_user;
GRANT USAGE, SELECT ON SEQUENCE flight_summaries_id_seq TO airline_tracker_user;

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'Database initialized successfully for Airline Tracker System';
END $$;

