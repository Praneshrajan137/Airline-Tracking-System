import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { FlightData } from './types';

const candidates: FlightData[] = [
  {
    fa_flight_id: 'fa-flight-old', ident: 'UAL123', status: 'Landed',
    scheduled_out: '2026-11-16T08:00:00-08:00', actual_out: '2026-11-16T08:05:00-08:00',
    scheduled_in: '2026-11-16T16:00:00-05:00', actual_in: '2026-11-16T16:10:00-05:00',
    origin: 'KSEA', destination: 'KEWR', origin_name: 'Seattle-Tacoma International',
    origin_city: 'Seattle', origin_timezone: 'America/Los_Angeles',
    destination_name: 'Newark Liberty International', destination_city: 'Newark',
    destination_timezone: 'America/New_York', aircraft_type: 'Boeing 737',
    latitude: null, longitude: null, altitude: null, groundspeed: null,
    last_position_at: null, checked_at: '2026-11-16T17:00:00Z', data_source: 'Demo fixture',
  },
  {
    fa_flight_id: 'fa-flight-current', ident: 'UAL123', status: 'En Route',
    scheduled_out: '2026-11-17T08:00:00-08:00', actual_out: '2026-11-17T08:05:00-08:00',
    scheduled_in: '2026-11-17T16:00:00-05:00', actual_in: null,
    origin: 'KSEA', destination: 'KEWR', origin_name: 'Seattle-Tacoma International',
    origin_city: 'Seattle', origin_timezone: 'America/Los_Angeles',
    destination_name: 'Newark Liberty International', destination_city: 'Newark',
    destination_timezone: 'America/New_York', aircraft_type: 'Boeing 737',
    latitude: 42, longitude: -76, altitude: 35000, groundspeed: 450,
    last_position_at: '2026-11-17T15:00:00Z', checked_at: '2026-11-17T15:02:00Z',
    data_source: 'Demo fixture',
  },
];

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe('guest flight journey', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('keeps the selected flight ID through summary failure and retry', async () => {
    let summaryReads = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/candidates')) return jsonResponse(candidates);
      if (url.endsWith('/instance/fa-flight-current/summary/retry') && init?.method === 'POST') {
        return jsonResponse({ accepted: true }, 202);
      }
      if (url.endsWith('/summary/fa-flight-current')) {
        summaryReads += 1;
        return summaryReads === 1
          ? jsonResponse({ error: 'Summary unavailable', message: 'Please retry.' }, 503)
          : jsonResponse({
              ident: 'UAL123',
              fa_flight_id: 'fa-flight-current',
              summary_text: 'Demo summary for the selected flight.',
              generated_at: '2026-11-17T15:02:00Z',
            });
      }
      return jsonResponse({ error: 'Unexpected request', message: url }, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<App />);
    expect(screen.getByText('No account required')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'UAL123' }));

    const matchingCandidates = await screen.findAllByRole('button', { name: /Seattle.*Newark/ });
    expect(screen.getAllByText('Demo fixture · sample data')).toHaveLength(2);
    const selectedCandidate = matchingCandidates[1];
    await user.click(selectedCandidate);
    expect(selectedCandidate.getAttribute('aria-pressed')).toBe('true');

    await user.click(await screen.findByRole('button', { name: 'Retry' }));

    await screen.findByText('Demo summary for the selected flight.');
    expect(screen.getByText(/flight instance fa-flight-current/)).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/flight/UAL123/instance/fa-flight-current/summary/retry',
      { method: 'POST', signal: expect.any(AbortSignal) },
    );
    await waitFor(() => expect(summaryReads).toBe(2));
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/flight/UAL123/summary/fa-flight-current',
      { signal: expect.any(AbortSignal) },
    );
  });

  it('requests a summary only after a candidate is selected', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/candidates')) return jsonResponse(candidates);
      if (url.endsWith('/summary/fa-flight-current')) {
        return jsonResponse({ error: 'Summary not ready' }, 404);
      }
      if (url.endsWith('/instance/fa-flight-current/summary/retry') && init?.method === 'POST') {
        return jsonResponse({ status: 'queued' }, 202);
      }
      return jsonResponse({ error: 'Unexpected request', message: url }, 404);
    });
    vi.stubGlobal('fetch', fetchMock);

    const user = userEvent.setup();
    render(<App />);
    await user.click(screen.getByRole('button', { name: 'UAL123' }));
    const matchingCandidates = await screen.findAllByRole('button', { name: /Seattle.*Newark/ });
    expect(screen.getAllByText('Demo fixture · sample data')).toHaveLength(2);
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/summary/retry'))).toBe(false);

    await user.click(matchingCandidates[1]);
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/flight/UAL123/instance/fa-flight-current/summary/retry',
      { method: 'POST', signal: expect.any(AbortSignal) },
    ));
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/summary/retry'))).toHaveLength(1);
  });
});
