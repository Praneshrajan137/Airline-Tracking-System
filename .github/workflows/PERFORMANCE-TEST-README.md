# Performance gate

`performance-tests/` contains a Gatling Java simulation run by the blocking `performance-gate` job in `ci-cd.yml`. The job starts the isolated Compose stack with `docker-compose.perf.yml`, which enables deterministic, visibly labeled fixtures and keeps Redis caching active.

| Assertion | Target |
| --- | ---: |
| Controlled cache-miss response time | p95 < 2,000 ms |
| Warm cache-hit response time | p95 < 500 ms |
| Success rate for 100 concurrent cache hits | > 95% |

The simulation warms `UAL123`, requests a controlled `BA178` miss, then issues 100 concurrent cached requests. It makes no live or paid provider calls. Reports are uploaded from `performance-tests/target/gatling/` by CI.

These are synthetic local gates. A passing report is evidence only for the captured software and test environment; it is not a production SLO, capacity guarantee, or traveler outcome result. No passing run is recorded until the CI or local Gatling report exists.

Run locally from the repository root:

```powershell
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml up --build -d --wait --wait-timeout 360
.\mvnw.cmd -B -ntp -pl performance-tests -Pperformance-gate gatling:test -Dtarget.baseUrl=http://localhost:8080
docker compose -f integration-tests/docker-compose.e2e.yml -f integration-tests/docker-compose.perf.yml down --remove-orphans
```