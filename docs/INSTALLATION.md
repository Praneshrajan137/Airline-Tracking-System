# Installation and verification

## Prerequisites

- Eclipse Temurin JDK 25 (the project compiles for Java 25).
- Docker Desktop using the Linux container engine for Compose stacks.
- Git.
- Node.js 24 and npm 11 only if you want to run the React frontend and browser checks.

Maven is pinned by the repository wrapper to Maven 3.9.16. The wrapper downloads its bootstrap and Maven distribution on first run; a separate Maven installation is not required.

## Check the toolchain

```powershell
java -version
javac -version
.\mvnw.cmd -version
node --version
npm --version
```

Use `mvnw.cmd` in PowerShell. On Linux or macOS, run `sh ./mvnw`.

## Build and unit-test the Java reactor

From the repository root:

```powershell
.\mvnw.cmd -B -ntp clean verify
```

This builds the four services, the integration-test module, and the performance-test module. Container-backed service tests need a working Docker engine.

## Run the local demo

The local launcher keeps any existing `.env` and starts the fixture-backed demo:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
.\scripts\deploy.ps1
```

Open `http://localhost` after the Compose health checks pass. The demo uses clearly labeled local fixtures and deterministic summary text; it needs no provider credentials and does not call FlightAware or OpenAI. Stop it with `docker compose down`.

## Full-stack Compose E2E and recovery checks

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl integration-tests -am -Pcompose-e2e verify
docker compose -f integration-tests/docker-compose.e2e.yml down --remove-orphans
```

The E2E network is isolated and uses WireMock for FlightAware/OpenAI endpoints. The Java suite covers the selected-flight path, cache reuse, provider errors, and summary recovery after a provider rate limit. The gate does not make paid or live provider calls.

## Browser and performance gates

The browser test uses Playwright with deterministic API fixtures:

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run e2e
```

Run the synthetic performance gate against the fixture-only Compose overlay:

```powershell
cd ..
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl performance-tests -Pperformance-gate gatling:test -Dtarget.baseUrl=http://localhost:8080
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml down --remove-orphans
```

The Gatling assertions are cache-hit p95 below 500 ms, one controlled cache-miss p95 below 2 seconds, and greater than 95% successful requests for 100 concurrent cached lookups. These are local synthetic gates, not production SLO evidence. HTML output is written under `performance-tests/target/gatling/`.

## Live providers

Live mode is opt-in. Review the provider's current terms and plan limits before using credentials. Supply secrets through a local secret store or deployment secret manager; never commit `.env`. No live or paid provider request is part of the normal build, E2E, browser, or performance checks.

For operating details and known limits, see [RUNBOOK.md](RUNBOOK.md). For current evidence and unverified claims, see [PROJECT_COMPLETION_REPORT.md](PROJECT_COMPLETION_REPORT.md).