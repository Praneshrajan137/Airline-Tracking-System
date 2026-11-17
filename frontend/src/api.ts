export class RequestTimeoutError extends Error {
  constructor() {
    super('The request took too long.');
    this.name = 'RequestTimeoutError';
  }
}

export interface TimedJsonResponse<T> {
  response: Response;
  data: T | undefined;
}

/** Apply one deadline to both response headers and the complete JSON body. */
export async function fetchJsonWithTimeout<T>(
  input: RequestInfo | URL,
  init: RequestInit = {},
  timeoutMs = 10_000,
): Promise<TimedJsonResponse<T>> {
  const controller = new AbortController();
  const upstreamSignal = init.signal;
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let timedOut = false;
  const forwardAbort = () => controller.abort(upstreamSignal?.reason);
  if (upstreamSignal?.aborted) forwardAbort();
  else upstreamSignal?.addEventListener('abort', forwardAbort, { once: true });

  const request = (async () => {
    const response = await fetch(input, { ...init, signal: controller.signal });
    const data = await response.json().catch(() => undefined) as T | undefined;
    return { response, data };
  })();

  const timeout = new Promise<never>((_, reject) => {
    timeoutId = setTimeout(() => {
      timedOut = true;
      controller.abort();
      reject(new RequestTimeoutError());
    }, timeoutMs);
  });

  try {
    return await Promise.race([request, timeout]);
  } catch (error) {
    if (timedOut) throw new RequestTimeoutError();
    throw error;
  } finally {
    if (timeoutId !== undefined) clearTimeout(timeoutId);
    upstreamSignal?.removeEventListener('abort', forwardAbort);
  }
}
