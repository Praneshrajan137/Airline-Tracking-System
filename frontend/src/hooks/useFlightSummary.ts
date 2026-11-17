import { useState, useEffect, useCallback, useRef } from 'react';
import { FlightSummary, ApiError } from '../types';
import { fetchJsonWithTimeout, RequestTimeoutError } from '../api';

const API_BASE = '/api/v1';
const MAX_WAIT_MS = 20_000;
const REQUEST_TIMEOUT_MS = 10_000;
const POLL_INTERVAL_MS = 2500;

export function useFlightSummary(ident: string | null, faFlightId: string | null) {
  const [summary, setSummary] = useState<FlightSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [retryRequest, setRetryRequest] = useState<{ faFlightId: string; sequence: number } | null>(null);
  const retrySequence = useRef(0);

  useEffect(() => {
    if (!ident || !faFlightId) {
      setSummary(null);
      setError(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    const requestController = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    const deadline = Date.now() + MAX_WAIT_MS;
    let summaryRequested = false;
    let requestRejected = false;
    setSummary(null);
    setError(null);
    setLoading(true);

    const summaryUrl = `${API_BASE}/flight/${encodeURIComponent(ident)}/summary/${encodeURIComponent(faFlightId)}`;
    const retryUrl = `${API_BASE}/flight/${encodeURIComponent(ident)}/instance/${encodeURIComponent(faFlightId)}/summary/retry`;

    const remainingTime = () => Math.max(0, deadline - Date.now());

    const readError = (response: Response, data: unknown, title: string): ApiError => {
      if (data && typeof data === 'object' && 'message' in data && typeof data.message === 'string') {
        return data as ApiError;
      }
      return {
        error: title,
        message: `The summary service returned HTTP ${response.status}.`,
      };
    };

    const requestSummary = async () => {
      const remaining = remainingTime();
      if (remaining <= 0) throw new RequestTimeoutError();
      const { response, data } = await fetchJsonWithTimeout<ApiError>(
        retryUrl,
        { method: 'POST', signal: requestController.signal },
        Math.min(REQUEST_TIMEOUT_MS, remaining),
      );
      if (cancelled) return;
      if (!response.ok) {
        setError(readError(response, data, 'Could not request summary'));
        requestRejected = true;
        return;
      }
      summaryRequested = true;
    };

    const finishAtDeadline = () => {
      setError({
        error: 'Summary still unavailable',
        message: 'No summary appeared within about 20 seconds. You can retry this selected flight.',
      });
      setLoading(false);
    };

    const scheduleNextPoll = () => {
      const remaining = remainingTime();
      if (remaining <= 0) {
        finishAtDeadline();
        return;
      }
      timer = setTimeout(poll, Math.min(POLL_INTERVAL_MS, remaining));
    };

    const poll = async () => {
      timer = undefined;
      const remaining = remainingTime();
      if (remaining <= 0) {
        finishAtDeadline();
        return;
      }

      try {
        const { response, data } = await fetchJsonWithTimeout<FlightSummary | ApiError>(
          summaryUrl,
          { signal: requestController.signal },
          Math.min(REQUEST_TIMEOUT_MS, remaining),
        );
        if (cancelled) return;

        if (response.status === 404) {
          if (!summaryRequested) await requestSummary();
          if (requestRejected) return;
          if (!cancelled) scheduleNextPoll();
          return;
        }
        if (!response.ok) {
          setError(readError(response, data, 'Summary unavailable'));
          return;
        }

        const result = data as FlightSummary | undefined;
        if (cancelled) return;
        if (!result || typeof result.fa_flight_id !== 'string' || typeof result.ident !== 'string'
          || typeof result.summary_text !== 'string') {
          setError({
            error: 'Invalid summary response',
            message: 'The summary service returned incomplete data. Please retry this flight.',
          });
          return;
        }
        if (result.fa_flight_id !== faFlightId || result.ident?.toUpperCase() !== ident.toUpperCase()) {
          setError({
            error: 'Summary does not match this flight',
            message: 'The returned summary was not for the selected flight instance. Please retry.',
          });
          return;
        }
        setSummary(result);
      } catch (requestError) {
        if (!cancelled) {
          setError({
            error: requestError instanceof RequestTimeoutError ? 'Summary request timed out' : 'Connection interrupted',
            message: requestError instanceof RequestTimeoutError
              ? 'The summary service did not respond in time. You can retry this flight.'
              : 'The summary service could not be reached. You can retry this flight.',
          });
        }
      } finally {
        if (!cancelled && timer === undefined) setLoading(false);
      }
    };

    const start = async () => {
      if (retryRequest?.faFlightId === faFlightId) {
        try {
          await requestSummary();
          if (cancelled || requestRejected) {
            setLoading(false);
            return;
          }
        } catch (requestError) {
          if (!cancelled) {
            setError({
              error: requestError instanceof RequestTimeoutError ? 'Summary request timed out' : 'Could not request summary',
              message: requestError instanceof RequestTimeoutError
                ? 'The flight service did not respond in time. You can retry.'
                : 'The flight service could not accept the summary request. You can retry.',
            });
          }
          setLoading(false);
          return;
        }
      }
      if (!cancelled) void poll();
    };

    void start();
    return () => {
      cancelled = true;
      requestController.abort();
      if (timer !== undefined) clearTimeout(timer);
    };
  }, [ident, faFlightId, retryRequest]);

  const refetch = useCallback(() => {
    if (faFlightId) {
      setRetryRequest({ faFlightId, sequence: ++retrySequence.current });
    }
  }, [faFlightId]);
  return { summary, loading, error, refetch };
}
