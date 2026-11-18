# Full-stack Compose E2E

## Requirements

- JDK 25 and the repository Maven Wrapper.
- Docker Desktop using the Linux container engine.

The stack publishes test endpoints only to `127.0.0.1`. Its bridge network is internal and points provider calls to WireMock, so the running services have no route to live FlightAware or OpenAI APIs.

## Run

From the repository root:

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl integration-tests -am -Pcompose-e2e verify
docker compose -f integration-tests/docker-compose.e2e.yml down --remove-orphans
```

The `compose-e2e` Maven profile runs the cross-service Java suite with Failsafe. The default Surefire run excludes that suite because it requires the Compose stack.

Scenarios cover candidate lookup, selection by `fa_flight_id`, cache reuse, provider not-found/error handling, and summary recovery after a mocked OpenAI rate limit. Reports are under `integration-tests/target/failsafe-reports/`.

## Performance overlay

The performance overlay sets `DEMO_ENABLED=true`, keeping candidate data deterministic and visibly labeled while preserving Redis caching. It tests one controlled miss followed by 100 simultaneous cached lookups:

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl performance-tests -Pperformance-gate gatling:test -Dtarget.baseUrl=http://localhost:8080
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml down --remove-orphans
```

The gate checks cached lookup p95 `<500 ms`, controlled miss p95 `<2 s`, and success `>95%` at 100 concurrent cached requests. These synthetic local results must not be described as production SLOs. Gatling reports are under `performance-tests/target/gatling/`.

## Browser journey

The browser journey runs against the local Vite server and intercepts API responses with deterministic fixtures; it does not require the Compose stack:

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run e2e
```

It checks guest search, source labels, explicit selection, and that a summary request is made only for the selected instance.

## Verification status

The tests and gates are implemented, but a passing result requires running them in the stated environment. No latency, availability, provider-live, user-value, or production-readiness result is claimed until the corresponding evidence exists.