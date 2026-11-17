# Local demo runbook

This runbook describes the academic Compose demo. It is not a production operations guide: production deployment, TLS termination, backups, alert delivery, high availability, and measured capacity have not been established.

## Start and check

1. Start Docker Desktop.
2. If `.env` does not exist, create it from `env.example` with `if (!(Test-Path .env)) { Copy-Item env.example .env }`; keep an existing `.env` so local port and credential values are preserved.
3. Run `scripts/deploy.ps1` from PowerShell or `bash scripts/deploy.sh` from a Unix shell. These launchers force the fixture-backed demo profile even if `.env` selects another profile.
4. Wait for service health checks, then open `http://localhost`.

The default profile is `demo`. Flight and summary providers are local fixtures; provider credentials are neither needed nor called. The frontend, gateway, and monitoring interfaces bind to loopback. PostgreSQL, Redis, Kafka, Eureka, and application service ports remain internal to Compose. Open Prometheus at `http://localhost:9090`, Grafana at `http://localhost:3000`, and Zipkin at `http://localhost:9411`. The local Grafana default is `admin` / `admin`; change it in `.env` before using a shared machine.

Useful checks:

```powershell
docker compose ps
curl.exe -i http://localhost:8080/actuator/health
curl.exe -i http://localhost:8080/api/v1/flight/UAL123/candidates
docker compose logs --tail 200 api-gateway flightdata-service llm-summary-service
```

Expected demo behavior: `UAL123` returns two dated flight instances; the UI asks which returned route and date the visitor means. `BA178` and `DL400` each return one. The selected record shows `Demo fixture`; its summary is fixed text and identifies itself as a demo summary. The page is a checked snapshot, not continuous tracking. Live FlightAware search does not accept a requested date and uses the provider's available window; its FAQ says ordinary endpoints generally return 48–72 hours by default, depending on the endpoint.

After the stack is running in the default `demo` profile, run `pwsh -File scripts/validate-demo.ps1` on Windows or `bash scripts/validate-demo.sh` on a system with `curl` and `jq`. These scripts verify the published frontend and gateway, selected-instance lookup, and matching demo summary. Do not run them against a live-provider stack; candidate lookup makes a provider request in that profile.

## Common failures

| Symptom | Check | Recovery |
| --- | --- | --- |
| Frontend is unavailable | `docker compose ps frontend` and frontend logs | Wait for image build/health check; then rerun the local demo launcher. |
| Gateway returns 5xx | Gateway, Redis, and registry health/logs | Restore the dependency. Rate limiting fails closed if Redis is unavailable. |
| No candidate appears | Confirm the ident is 2–3 letters plus 1–4 digits; inspect flight service logs | Retry `UAL123`, `BA178`, or `DL400` in demo mode. |
| Summary remains pending | Inspect Kafka and summary-service logs | Summary polling ends after about 20 seconds. Transient consumer failures retry with bounded backoff; OpenAI 429 responses use a valid `Retry-After` value when supplied. After retries are exhausted, the event is sent to `flight-data-events.DLT`; there is no automatic replay worker, so inspect and requeue only after the dependency recovers. The traveler can retry the selected flight from the UI. |
| Redis has old cache/quota entries | Inspect Redis only inside the Compose network | Cache entries expire after five minutes. Do not clear Redis in a shared environment; limiter counters affect provider spend controls. |
| Live profile cannot start | Check that provider keys and database password are present without printing their values | Supply secrets through a local secret manager or protected environment. Return to demo with `APP_PROFILE=demo` if provider access is not intended. |
| Prometheus target is down | Open `http://localhost:9090/targets` and check the service health/logs | Restore the service and confirm the `/actuator/prometheus` endpoint is available. |

## Live profile boundary

Live mode is for separately approved academic use after checking provider terms. Set `APP_PROFILE=prod` and provide `FLIGHTAWARE_API_KEY`, `OPENAI_API_KEY`, and a strong database password. Missing provider keys intentionally prevent startup. No provider SLA, live response, budget amount, or successful live deployment is claimed by this repository.

## Kafka retry and dead-letter setup

The summary service declares only the `<topic>.DLT` companion; the flight-data producer or Kafka operator owns the source topic. In Compose, set `KAFKA_TOPIC_FLIGHT_DATA` in `.env`; Compose passes the same `KAFKA_TOPIC` to both services. `KAFKA_TOPIC_PARTITIONS` and `KAFKA_TOPIC_REPLICATION_FACTOR` configure the DLT and default to `1` for the single-broker local demo. The custom summary consumer and DLT producer inherit Spring Kafka bootstrap and security settings, including optional SSL bundles. The DLT template overrides its key/value serializers and encodes `FlightData` as JSON. A failed broker send is a recovery failure, leaving the original record eligible for recovery rather than silently acknowledging it. A secured external broker still needs a deployment connectivity check.

For a managed Kafka cluster, inspect the source topic before startup and set the DLT partition count to at least the source partition count; recovery preserves the source partition number. [Spring Kafka's topic admin](https://docs.spring.io/spring-kafka/reference/kafka/configuring-topics.html) can increase an existing DLT when its declared partition count is higher; do not rely on it to shrink topics. This service does not declare or resize the source topic, so it will not change source key-to-partition routing. If broker auto-creation is disabled, provision the source topic separately. The summary service needs Kafka topic-admin permissions to create or describe the DLT; otherwise, pre-provision it and verify it exists before enabling summary consumption. Broker unavailability at startup may be logged while the service continues, so healthy application startup alone does not prove DLT readiness. The replication-factor setting applies when the DLT is created; changing replication later requires broker reassignment. Inspect both topic descriptions before rollout and keep source partition changes as an explicit migration because they can remap keyed records. The application does not replay DLT records automatically. Review and requeue a record only after the underlying provider or data problem is fixed.

## Shutdown and data

`docker compose down` stops containers and preserves named volumes. `docker compose down -v` deletes PostgreSQL, Redis, Kafka, and monitoring data; run it only when intentionally resetting the local demo. To resume the demo safely, rerun the local launcher.

## Logs and privacy

Logs should be reviewed for errors and provider status, not used as proof of source accuracy. Provider response bodies and API keys are not intentionally logged by the clients. Do not paste `.env` or secret values into issues or logs.
