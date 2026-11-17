import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchJsonWithTimeout, RequestTimeoutError } from './api';

describe('fetchJsonWithTimeout', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('aborts a request and reports a timeout when it exceeds its deadline', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | undefined;
    const fetchMock = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      signal = init?.signal as AbortSignal;
      return new Promise<Response>((_resolve, reject) => {
        signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const request = fetchJsonWithTimeout('/slow', {}, 5_000);
    const rejected = expect(request).rejects.toBeInstanceOf(RequestTimeoutError);
    await vi.advanceTimersByTimeAsync(5_000);

    await rejected;
    expect(signal?.aborted).toBe(true);
  });

  it('keeps the deadline active while reading a stalled JSON body', async () => {
    vi.useFakeTimers();
    let signal: AbortSignal | undefined;
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      signal = init?.signal as AbortSignal;
      return {
        ok: true,
        status: 200,
        json: () => new Promise<never>(() => undefined),
      } as unknown as Response;
    });
    vi.stubGlobal('fetch', fetchMock);

    const request = fetchJsonWithTimeout('/stalled-body', {}, 5_000);
    const rejected = expect(request).rejects.toBeInstanceOf(RequestTimeoutError);
    await vi.advanceTimersByTimeAsync(5_000);

    await rejected;
    expect(signal?.aborted).toBe(true);
  });
});
