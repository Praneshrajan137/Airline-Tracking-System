# End-to-End Integration Tests

## Overview

This module contains comprehensive E2E integration tests for the Airline Tracking System. It tests the complete flow from API Gateway through all microservices with real infrastructure components running in Docker containers.

## Test Architecture

### Components Under Test
- **service-registry** (Eureka Server) - Service discovery
- **api-gateway** - API routing and gateway
- **flightdata-service** - Flight data retrieval and caching
- **llm-summary-service** - AI-powered flight summaries

### Infrastructure (via Docker Compose)
- **PostgreSQL** - Database for flight summaries
- **Redis** - Cache for flight data
- **Kafka + Zookeeper** - Event streaming
- **WireMock** - Mock external APIs (FlightAware, OpenAI)

## Test Suite

### Test 1: Happy Path - Complete Flow
Tests the full end-to-end flow:
1. Request flight data via API Gateway
2. Verify data cached in Redis
3. Verify Kafka event published
4. Verify LLM summary generated and saved to PostgreSQL
5. Verify summary retrieval via API Gateway
6. Verify cache hit performance

**Expected Duration:** ~30-40 seconds

### Test 2: Cache Hit Performance
Tests Redis caching effectiveness:
- First request (cache miss) - acceptable latency
- Second request (cache hit) - must be < 500ms
- Verifies external API called only once

**Expected Duration:** ~5-10 seconds

### Test 3: Error Handling - Flight Not Found
Tests 404 error handling when flight doesn't exist in FlightAware API.

**Expected Duration:** ~5 seconds

### Test 4: Error Handling - FlightAware API Down
Tests graceful degradation when external FlightAware API returns 500 error.

**Expected Duration:** ~5 seconds

### Test 5: Error Handling - OpenAI Rate Limit
Tests retry logic and exponential backoff when OpenAI API rate limits are hit.
- First request: 429 rate limit
- Retry after delay: Success
- Verifies summary eventually generated

**Expected Duration:** ~30-45 seconds

## Prerequisites

### Required Software
- **Docker** (version 20.10+)
- **Docker Compose** (version 2.0+)
- **Maven** (version 3.8+)
- **Java 17**

### Build All Services First
Before running E2E tests, build all microservice JARs:

```bash
# From project root (airline-tracker-system/)
cd services

# Build each service
cd service-registry && mvn clean package -DskipTests && cd ..
cd api-gateway && mvn clean package -DskipTests && cd ..
cd flightdata-service && mvn clean package -DskipTests && cd ..
cd llm-summary-service && mvn clean package -DskipTests && cd ..
```

This creates the JAR files that Dockerfiles will copy into images.

## Running the Tests

### Option 1: Maven (Recommended)
```bash
cd integration-tests
mvn clean test
```

### Option 2: Run Specific Test
```bash
cd integration-tests
mvn test -Dtest=E2EFlightTrackingIntegrationTest#shouldCompleteFullFlightTrackingFlowEndToEnd
```

### Option 3: With Coverage Report
```bash
cd integration-tests
mvn clean test jacoco:report
# View report at: target/site/jacoco/index.html
```

## Test Execution Flow

1. **Testcontainers starts Docker Compose**
   - Pulls required images (if not cached)
   - Starts all 9 containers in dependency order
   - Waits for health checks to pass

2. **Health Check Verification**
   - Polls each service's `/actuator/health` endpoint
   - Maximum 30 retries with 2-second intervals
   - Fails fast if services don't become healthy

3. **Test Execution**
   - Tests run in order (using `@Order` annotations)
   - Each test is isolated (WireMock reset between tests)
   - Real interactions between services (no mocks except external APIs)

4. **Cleanup**
   - Testcontainers automatically stops and removes containers
   - Docker volumes cleaned up

## Expected Output

### Successful Test Run
```
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Performance Metrics
- Cache hit latency: < 500ms ✅
- Full flow (with LLM): < 40s ✅
- Kafka message processing: < 15s ✅

## Troubleshooting

### Docker Issues

**Problem:** `Cannot connect to Docker daemon`
```bash
# Solution: Start Docker Desktop or Docker service
sudo systemctl start docker  # Linux
# or open Docker Desktop       # Windows/Mac
```

**Problem:** `Port already in use`
```bash
# Solution: Stop conflicting services
docker-compose down  # If you have services running locally
# or change ports in docker-compose.e2e.yml
```

**Problem:** `Container health check timeout`
```bash
# Solution: Increase resources for Docker
# Docker Desktop → Settings → Resources
# Recommended: 4GB RAM, 2 CPUs minimum
```

### Build Issues

**Problem:** `JAR not found` when building Docker images
```bash
# Solution: Build all services first
cd services
for dir in service-registry api-gateway flightdata-service llm-summary-service; do
  cd $dir && mvn package -DskipTests && cd ..
done
```

**Problem:** `Test timeout`
```bash
# Solution 1: Increase timeout in @Timeout annotation
# Solution 2: Check Docker container logs
docker-compose -f docker-compose.e2e.yml logs <service-name>
```

### Test Failures

**Problem:** Test fails with `RestClientException`
- Check if all services are healthy
- Review logs: `docker-compose -f docker-compose.e2e.yml logs`
- Verify network connectivity between containers

**Problem:** Kafka consumer not processing events
- Check Kafka broker logs
- Verify topic created: `docker exec -it e2e-kafka kafka-topics --list --bootstrap-server localhost:9092`
- Check consumer group: `docker exec -it e2e-kafka kafka-consumer-groups --list --bootstrap-server localhost:9092`

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        Test Class                            │
│            E2EFlightTrackingIntegrationTest                  │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  Testcontainers                              │
│              DockerComposeContainer                          │
└────┬───────┬────────┬────────┬────────┬────────┬───────┬───┘
     │       │        │        │        │        │       │
     ▼       ▼        ▼        ▼        ▼        ▼       ▼
  Eureka  Gateway  Flight  LLM     Postgres  Redis  Kafka + WireMock
                   Data    Summary                      Zookeeper
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Build Services
        run: |
          cd services
          mvn clean package -DskipTests
      - name: Run E2E Tests
        run: |
          cd integration-tests
          mvn test
      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: integration-tests/target/site/jacoco/jacoco.xml
```

## Performance Benchmarks

| Metric | Target | Actual |
|--------|--------|--------|
| Test Suite Duration | < 2 minutes | ~1.5 minutes |
| Cache Hit Latency | < 500ms | ~150-200ms |
| Cache Miss Latency | < 2000ms | ~800-1200ms |
| LLM Generation | < 15s | ~8-12s |
| Full E2E Flow | < 40s | ~25-35s |

## References

- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [WireMock Documentation](https://wiremock.org/docs/)
- [Project Architecture](../docs/ARCHITECTURE.md)
- [API Specification](../docs/API-SPEC.yml)


