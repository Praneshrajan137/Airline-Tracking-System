import { useState, FormEvent } from 'react';
import './FlightSearch.css';

const FLIGHT_IDENT_REGEX = /^[A-Z]{2,3}\d{1,4}$/i;

interface FlightSearchProps {
  onSearch: (ident: string) => void;
  loading: boolean;
}

export default function FlightSearch({ onSearch, loading }: FlightSearchProps) {
  const [input, setInput] = useState('');
  const [validationError, setValidationError] = useState('');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = input.trim();

    if (!trimmed) {
      setValidationError('Please enter a flight number');
      return;
    }

    if (!FLIGHT_IDENT_REGEX.test(trimmed)) {
      setValidationError('Format: 2-3 letters + 1-4 digits (e.g., UAL123, BA456)');
      return;
    }

    setValidationError('');
    onSearch(trimmed);
  };

  return (
    <form className="flight-search" onSubmit={handleSubmit}>
      <div className="search-input-wrapper">
        <input
          type="text"
          className="flight-search-input"
          placeholder="Enter flight number (e.g., UAL123)"
          value={input}
          onChange={(e) => {
            setInput(e.target.value.toUpperCase());
            if (validationError) setValidationError('');
          }}
          disabled={loading}
          aria-label="Flight number"
          maxLength={7}
        />
        <button
          type="submit"
          className="search-button"
          disabled={loading || !input.trim()}
          aria-label="Search flight"
        >
          {loading ? 'Searching...' : 'Search Flight'}
        </button>
      </div>
      {validationError && (
        <p className="validation-error" role="alert">
          {validationError}
        </p>
      )}
    </form>
  );
}
