# Airline Tracker

Airline Tracker is an academic and portfolio project for travelers and people following a flight. Look up a flight number, choose a returned dated route when several instances match, then see the selected instance’s reported status and a summary tied to that exact flight ID. This is a checked snapshot lookup, not continuous flight tracking.

**Project status:** working portfolio prototype. The local demo uses sample records. Live FlightAware data and live OpenAI summaries require provider credentials and have not been validated in this session. The project has no public-service, accuracy, uptime, load-capacity, or production-readiness claim.

**Frontier implementation:** the Java reactor targets Java 25, Spring Boot 4.1.2, and Spring Cloud 2025.1.2. A pinned Maven Wrapper, full-stack recovery suite, Playwright journey, and Gatling gates are included. Test targets are not measured results; full runtime and participant evidence remain pending.

## What the product does

1. Search a flight number without creating an account.
2. Choose a returned dated flight instance when the number has multiple matches. Live search does not accept a requested date; FlightAware says its ordinary endpoints generally return 48–72 hours of data by default, depending on the endpoint.
3. Review the route, airport-local scheduled and actual times, status, last position timestamp, data source, and data check time.
4. Read the summary for the selected FlightAware flight ID. The demo labels its fixture text and does not call an AI provider.

The flight number alone is not a unique trip. The API therefore returns candidates and uses `fa_flight_id` to keep flight details and summaries attached to the selected instance.

## Run the offline demo

Requirements: Docker Desktop with Compose and enough local memory for PostgreSQL, Redis, Kafka, and the four Java services.

```powershell
if (!(Test-Path .env)) { Copy-Item env.example .env } else { Write-Host ".env already exists; keeping it." }
.\scripts\deploy.ps1
```

The launcher preserves an existing `.env` and forces the fixture-backed demo profile for this start. Wait for Compose health checks, then open [http://localhost](http://localhost) and run `pwsh -File scripts/validate-demo.ps1`. The frontend, gateway, Prometheus, Grafana, and Zipkin bind to loopback; databases, brokers, service registry, and application service ports remain internal to Compose. The demo needs no provider keys and uses fixtures for `UAL123`, `BA178`, and `DL400`. `UAL123` returns two instances so the selection flow can be demonstrated. The local default Grafana login is `admin` / `admin`; change it in `.env` before using this stack on a shared machine.

Monitoring tools are available at `http://localhost:9090` (Prometheus), `http://localhost:3000` (Grafana), and `http://localhost:9411` (Zipkin). Override their loopback ports with `PROMETHEUS_PORT`, `GRAFANA_PORT`, and `ZIPKIN_PORT` if needed.

Check service state and logs:

```powershell
docker compose ps
docker compose logs -f frontend api-gateway flightdata-service llm-summary-service
```

Stop the stack with `docker compose down`. The named database and broker volumes remain on disk.

## Enable live provider calls

This project is scoped to academic and portfolio use. Before using provider data, review the current [FlightAware AeroAPI terms and plan limits](https://www.flightaware.com/commercial/aeroapi/) for your intended use. Do not expose a public or commercial service based on this repository without separately reviewing the provider license and operating requirements.

Set `APP_PROFILE=prod`, then supply `FLIGHTAWARE_API_KEY`, `OPENAI_API_KEY`, and a strong `POSTGRES_PASSWORD` through a local secret store or deployment secret manager. The live summary model defaults to `gpt-6-luna`; `OPENAI_MODEL` can override it. Missing provider keys stop the corresponding service at startup. Provider requests are bounded by a Redis-backed client limit at the gateway and global quota counters at the flight service; if the limiter store is unavailable, provider calls fail closed.

**Live-mode checks remain open:** provider entitlements for the exact use case, current model access and request parameters, actual API behavior, billing limits, and full-stack operation with real credentials. No live paid request was made as part of this work.

## Architecture

The application keeps four Spring services: service registry, API gateway, flight data, and summary. PostgreSQL stores summaries; Redis caches flight candidates and enforces FlightAware and OpenAI request limits; Kafka carries only a selected flight instance to the summary service; React presents the traveler flow.

The local demo profile swaps provider calls for fixture data and fixed summary text. The live profile uses FlightAware AeroAPI and OpenAI. External provider calls do not happen in demo mode.

API details and runtime operations:

- [OpenAPI specification](docs/API-SPEC.yml)
- [Runbook](docs/RUNBOOK.md)
- [Pitch and demo walkthrough](docs/PITCH-AND-DEMO.md)
- [Claims ledger](docs/CLAIMS-LEDGER.md)
- [Current project status and evidence](docs/PROJECT_COMPLETION_REPORT.md)
- [Five-session traveler research protocol](docs/TRAVELER-RESEARCH.md)
- [Installation and verification commands](docs/INSTALLATION.md)
- [Local demo smoke check](scripts/validate-demo.ps1) (PowerShell) or [Bash version](scripts/validate-demo.sh)

## Build and verify

Run the Java reactor from the repository root:

```powershell
.\mvnw.cmd -B -ntp clean verify
Push-Location frontend; npm ci; npm test; npm run build; Pop-Location
```

The full-stack Compose recovery suite, browser journey, and performance gate commands are in [RUN_E2E_TESTS.md](integration-tests/RUN_E2E_TESTS.md). The performance targets are cached p95 `<500 ms`, controlled miss p95 `<2 s`, and `>95%` success for 100 concurrent cached lookups. They are synthetic acceptance targets, not a production SLO or a measured result.

## Scope and limits

- This is not an airline-operated source of truth. Provider records can be delayed, incomplete, or corrected after they are fetched.
- Times are shown in the airport’s timezone when the source provides one; the service check time is labeled in the viewer’s device timezone.
- Position age is shown from the provider timestamp and is never described as live when its timestamp is missing.
- Accounts, saved flights, push alerts, mobile apps, and public multi-tenant deployment are out of scope.
- No user research has yet established that the flow solves travelers’ needs better than existing flight trackers.
