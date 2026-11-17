# Compose integration suite

The Maven test module exercises the current gateway-to-service flow against PostgreSQL, Redis, Kafka, Eureka, and WireMock. WireMock stands in for FlightAware and OpenAI, so this suite does not need provider credentials or paid calls.

## Scenarios

- Search candidates, retrieve one selected flight ID, request its summary, and retrieve that exact summary.
- Confirm repeated lookups use the Redis cache.
- Check provider not-found and provider-error handling.
- Exercise OpenAI rate-limit recovery.

The suite starts no containers itself. The Compose stack must be running first, and the tests connect to fixed localhost ports. The Compose file binds every published test port to `127.0.0.1`; it uses intentionally simple test credentials and must not be exposed to an untrusted network. Stop any other local stack using those ports before starting this one.

## Run

From the repository root:

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml config --quiet
docker compose -f integration-tests/docker-compose.e2e.yml up --build -d
```

Wait until the services report healthy, then run:

```powershell
cd integration-tests
mvn -B -ntp test
cd ..
docker compose -f integration-tests/docker-compose.e2e.yml down
```

Use `down -v` only if you intend to remove the test database and broker volumes.

## Verification status

This harness was updated to use the candidate, selected-instance, and explicit summary-request routes. Its Compose configuration and Java test compilation can be checked without starting containers. A passing E2E run still requires a working Docker engine; no latency, availability, throughput, coverage, or production-readiness result is claimed here.
