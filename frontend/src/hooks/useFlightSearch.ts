import { useState, useCallback, useEffect, useRef } from 'react';
import { FlightData, ApiError } from '../types';
import { fetchJsonWithTimeout, RequestTimeoutError } from '../api';

const API_BASE = '/api/v1';

export function useFlightSearch() {
  const [candidates, setCandidates] = useState<FlightData[]>([]);
  const [flight, setFlight] = useState<FlightData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [searchedIdent, setSearchedIdent] = useState<string | null>(null);
  const sequence = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);

  useEffect(() => () => activeRequest.current?.abort(), []);

  const search = useCallback(async (ident: string) => {
    const normalized = ident.trim().toUpperCase();
    const currentSequence = ++sequence.current;
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setSearchedIdent(normalized);
    setLoading(true);
    setError(null);
    setFlight(null);
    setCandidates([]);
    let cancelled = false;

    try {
      const { response, data } = await fetchJsonWithTimeout<FlightData[] | ApiError>(
        `${API_BASE}/flight/${encodeURIComponent(normalized)}/candidates`,
        { signal: controller.signal },
      );
      if (!response.ok) {
        const err: ApiError = data && typeof data === 'object' && 'message' in data
          ? data as ApiError
          : {
          error: 'Request failed',
          message: `The flight service returned HTTP ${response.status}. Try again shortly.`,
          };
        if (currentSequence === sequence.current) setError(err);
        return;
      }

      const results = data;
      if (currentSequence !== sequence.current) return;
      if (!Array.isArray(results) || results.length === 0) {
        setError({ error: 'No matching flight', message: `No instances were returned for ${normalized} in the available data window. Check the flight number, or try again later if the trip is outside that window.` });
        return;
      }

      setCandidates(results);
      if (results.length === 1) setFlight(results[0]);
    } catch (error) {
      if (controller.signal.aborted && !(error instanceof RequestTimeoutError)) {
        cancelled = true;
        return;
      }
      if (currentSequence === sequence.current) {
        setError({
          error: error instanceof RequestTimeoutError ? 'Flight search timed out' : 'Service unavailable',
          message: error instanceof RequestTimeoutError
            ? 'The flight service did not respond in time. Try again shortly.'
            : 'The flight service could not be reached. Check your connection and try again.',
        });
      }
    } finally {
      if (!cancelled && currentSequence === sequence.current) setLoading(false);
      if (activeRequest.current === controller) activeRequest.current = null;
    }
  }, []);

  const selectFlight = useCallback((faFlightId: string) => {
    setFlight(candidates.find((candidate) => candidate.fa_flight_id === faFlightId) ?? null);
  }, [candidates]);

  return { candidates, flight, loading, error, searchedIdent, search, selectFlight };
}
