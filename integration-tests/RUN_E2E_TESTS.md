# Running E2E Integration Tests

## Prerequisites

1. **Docker & Docker Compose** installed and running
2. **All microservice JARs built** (see below)
3. **Maven 3.8+** and **Java 17**

## Step 1: Build All Service JARs

From the project root, build all 4 microservices:

```bash
cd airline-tracker-system/services

# Build each service
cd service-registry && mvn clean package -DskipTests && cd ..
cd api-gateway && mvn clean package -DskipTests && cd ..
cd flightdata-service && mvn clean package -DskipTests && cd ..
cd llm-summary-service && mvn clean package -DskipTests && cd ..
```

**✅ Verify JARs exist:**
- `service-registry/target/service-registry-1.0.0.jar`
- `api-gateway/target/api-gateway-1.0.0.jar`
- `flightdata-service/target/flightdata-service-1.0.0.jar`
- `llm-summary-service/target/llm-summary-service-1.0.0.jar`

## Step 2: Start Docker Compose Stack

From the integration-tests directory:

```bash
cd ../integration-tests
docker-compose -f docker-compose.e2e.yml up -d
```

**Wait for all services to be healthy** (~2-3 minutes):

```bash
docker-compose -f docker-compose.e2e.yml ps
```

All services should show `healthy` status.

## Step 3: Run E2E Tests

```bash
mvn clean test
```

## Step 4: Cleanup

Stop and remove all containers:

```bash
docker-compose -f docker-compose.e2e.yml down -v
```

---

## Alternative: One-Command E2E Test

Create a script to automate all steps:

### Windows (PowerShell)

```powershell
# run-e2e-tests.ps1
cd airline-tracker-system\services
mvn clean install -DskipTests
cd ..\integration-tests
docker-compose -f docker-compose.e2e.yml up -d
Start-Sleep -Seconds 60  # Wait for services
mvn test
docker-compose -f docker-compose.e2e.yml down -v
```

### Linux/Mac (Bash)

```bash
#!/bin/bash
# run-e2e-tests.sh
cd airline-tracker-system/services
mvn clean install -DskipTests
cd ../integration-tests
docker-compose -f docker-compose.e2e.yml up -d
sleep 60  # Wait for services
mvn test
docker-compose -f docker-compose.e2e.yml down -v
```

---

## Troubleshooting

### Services not starting

```bash
# Check logs
docker-compose -f docker-compose.e2e.yml logs service-registry
docker-compose -f docker-compose.e2e.yml logs api-gateway
```

### Port conflicts

If ports 8080, 8081, 8082, 8761 are already in use, stop conflicting services:

```bash
docker ps  # Find containers using these ports
docker stop <container-id>
```

### Clean slate restart

```bash
docker-compose -f docker-compose.e2e.yml down -v
docker system prune -f
docker-compose -f docker-compose.e2e.yml up -d --build
```

---

## Test Expected Results

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

All 5 E2E tests should pass:
1. ✅ Happy Path - Complete Flow
2. ✅ Cache Hit Performance
3. ✅ Error Handling - Flight Not Found
4. ✅ Error Handling - FlightAware API Down
5. ✅ Error Handling - OpenAI Rate Limit


