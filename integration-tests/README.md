# Integration and recovery tests

The cross-service Java suite requires the isolated Compose stack described in [RUN_E2E_TESTS.md](RUN_E2E_TESTS.md). Run it from the repository root:

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl integration-tests -am -Pcompose-e2e verify
docker compose -f integration-tests/docker-compose.e2e.yml down --remove-orphans
```

Compose runs Eureka, gateway, flight-data and summary services, Redis, PostgreSQL, Kafka, and WireMock on an internal network. Provider traffic is routed to WireMock, never to live or paid APIs. The E2E scenarios cover selected-instance lookup, cache reuse, provider failures, and summary rate-limit recovery.

The project also includes a browser journey in `frontend/e2e/` and a Gatling gate in `performance-tests/`. The browser test mocks API responses; the Gatling overlay uses labeled demo fixtures and Redis caching. Targets are cached p95 `<500 ms`, controlled miss p95 `<2 s`, and `>95%` success at 100 concurrent cached requests. These remain targets until reports from a completed run are recorded.

Use `docker compose down` to stop a stack. This file defines no named test volumes, so do not add `-v` unless you intend to remove volumes supplied by a local override.