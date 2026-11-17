# Local demo deployment

This document covers the reproducible local academic demo only. No public deployment target has been reviewed or tested.

## Requirements

- Docker Desktop with Compose
- At least 8 GB memory available to Docker is a practical starting point for this multi-container stack; this is an estimate, not a measured minimum

## Start

```powershell
if (!(Test-Path .env)) { Copy-Item env.example .env } else { Write-Host ".env already exists; keeping it." }
.\scripts\deploy.ps1
docker compose ps
```

The launcher forces `APP_PROFILE=demo` for the Compose command, preserves an existing `.env`, and uses local sample flights and fixed summary text. It needs no FlightAware or OpenAI key. Visit `http://localhost`. Frontend, gateway, Prometheus, Grafana, and Zipkin bind to loopback; other services are internal to the Compose network.

The launcher reports when containers have been launched; wait for health checks and run `pwsh -File scripts/validate-demo.ps1` before relying on the walkthrough.

## Live provider profile

Live mode is not a public deployment recipe. To exercise it in an approved academic setting, set `APP_PROFILE=prod` and provide `FLIGHTAWARE_API_KEY`, `OPENAI_API_KEY`, and a strong `POSTGRES_PASSWORD` through a protected secret source. Verify provider licensing, account/model access, current request parameters, quotas, and spend limits before enabling provider calls. Missing API keys cause the corresponding service to fail at startup.

No TLS, public ingress, SSO, secret manager integration, high availability, database migration strategy, backup restore, disaster recovery, or live provider trace has been demonstrated. The application is not production-ready.

## Stop

```powershell
docker compose down
```

This preserves named volumes. `docker compose down -v` permanently removes local database, cache, broker, and monitoring data.

For startup diagnostics and recovery steps, use [docs/RUNBOOK.md](docs/RUNBOOK.md).
