# Security boundary and known limits

## Intended use

The checked-in Compose stack is an academic local demo. It binds the frontend, API gateway, Prometheus, Grafana, and Zipkin to `127.0.0.1`. Internal application and infrastructure services use a shared Docker bridge network. This narrows local exposure, but is not a network security assessment or a deployment security guarantee.

## Authentication and access

- Flight lookup is public within the local gateway and does not require an account.
- Account endpoints are disabled in the gateway; service-side account code is inactive unless the `accounts` profile is explicitly enabled.
- Because lookup has no user identity, the gateway applies a Redis token bucket using the client address received from the bundled Nginx proxy (1 token/second, burst 5). Deployments behind another proxy need a reviewed trusted-proxy configuration; caller-supplied address headers must not be trusted.
- In the default demo Compose file, internal application and infrastructure ports are not published. The separate E2E Compose file publishes test ports on `127.0.0.1` only and uses test credentials; do not expose that stack to an untrusted network or run it as a public service.
- Grafana's local-only default login is `admin` / `admin`; set `GRAFANA_ADMIN_PASSWORD` in `.env` before using the monitoring interface on a shared machine.

## Provider keys and limits

- Demo mode exits before FlightAware or OpenAI provider requests and uses labeled fixtures.
- Live mode requires `FLIGHTAWARE_API_KEY` and `OPENAI_API_KEY`; do not commit either value. Supply them through a protected local environment or a deployment secret manager.
- FlightAware calls are constrained by a Redis-backed global quota. The default configuration is 10/minute, 200/hour, and 300/day; the `prod` profile uses stricter defaults. Limits must be reconciled with the specific provider contract before use.
- OpenAI requests are constrained by a Redis-backed global quota (3/minute, 10/hour, 100/day). Only a selected instance queues summary work. The quota is shared and anonymous, so a caller can exhaust it and deny summaries to other visitors. These count limits do not guarantee a maximum bill; verify provider pricing and account controls separately.
- If Redis quota storage fails, flight provider requests are rejected with service unavailable. The gateway’s Redis-backed client limiter also fails closed if Redis is unavailable.
- Compose uses Redis `noeviction` so memory pressure cannot LRU-evict provider quota counters; if Redis cannot write a counter, provider requests are rejected. Redis durability and billing reconciliation still need live operational testing.
- No provider cost, entitlement, or monthly cap is guaranteed by this repository.

## Data and infrastructure

- PostgreSQL, Redis, Kafka, Eureka, and monitoring services communicate over the same Compose network; they are not mutually isolated.
- Compose does not configure TLS between containers, database encryption, external secret management, backups, restore automation, or high availability.
- Actuator health and Prometheus metrics are available through the loopback gateway for local diagnostics; gateway route metadata is denied. Prometheus is unauthenticated in this local-only setup and must not be exposed publicly.
- Do not use live flight data or generated summaries as an operational source of truth. Provider delay, missing fields, and correction behavior have not been validated.

## Before any public deployment

Threat modeling, dependency and image scanning, TLS and trusted proxy setup, authentication/abuse policy, secrets management, least-privilege service accounts, network segmentation, database migration and backup/restore tests, observability/alert ownership, provider license review, and an independent security review are still required. The current project is not production-ready.
