# ✈️ Airline Tracker - Real-Time Flight Tracking with AI Summaries

[![CI/CD Pipeline](https://github.com/Praneshrajan137/Airline-Tracking-System/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/Praneshrajan137/Airline-Tracking-System/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-red.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

> Production-ready microservices system with event-driven architecture, Redis caching, and AI-powered summaries

**13,319 lines of code** | **40/40 tests passing** | **Sub-100ms cache responses** | **100% E2E test pass rate**

---

## 🚀 Quick Start

```bash
# 1. Clone and setup environment
git clone https://github.com/Praneshrajan137/Airline-Tracking-System.git
cd airline-tracker-system
cp env.example .env  # Add your API keys

# 2. Deploy with one command
./scripts/deploy.sh  # Linux/Mac
# OR
.\scripts\deploy.ps1  # Windows

# 3. Verify deployment
curl http://localhost:8080/actuator/health

# 4. Test API
curl http://localhost:8080/api/v1/flight/UAL123

# 5. View dashboards
open http://localhost:8761  # Eureka
open http://localhost:9090  # Prometheus
```

**System ready in ~5 minutes!** ⚡

---

## 🏗️ Architecture

```mermaid
graph TD
    User[Client] --> APIG[api-gateway:8080]
    
    subgraph "Spring Cloud Services"
        APIG --> SR[service-registry:8761]
        FDS[flightdata-service:8081] --> SR
        LLMS[llm-summary-service:8082] --> SR
        APIG -.discovers.-> SR
    end
    
    subgraph "Data Plane"
        FDS -->|1. Check Cache| REDIS[Redis Cache]
        FDS -->|2. Cache Miss| FA[FlightAware API]
        FDS -->|3. Write Cache| REDIS
        FDS -->|4. Publish Event| K[Kafka: flight-data-events]
        LLMS -->|5. Consume Event| K
        LLMS -->|6. Generate Summary| OpenAI[OpenAI API]
        LLMS -->|7. Save/Read| DB[PostgreSQL]
    end
    
    APIG --> FDS
    APIG --> LLMS
    FDS --> User
    LLMS --> User
```

### Microservices

| Service | Port | Description | Status |
|---------|------|-------------|--------|
| **service-registry** | 8761 | Eureka Server for service discovery | ✅ 5/5 tests |
| **api-gateway** | 8080 | Spring Cloud Gateway with load balancing | ✅ 5/5 tests |
| **flightdata-service** | 8081 | Real-time flight data + Redis caching | ✅ 5/5 tests |
| **llm-summary-service** | 8082 | AI summaries via OpenAI GPT-3.5-turbo | ✅ 25/25 tests |

---

## 🛠️ Technology Stack

### Core Technologies
- **Java 17** - Programming language
- **Spring Boot 3.2.0** - Application framework
- **Spring Cloud 2023.0.0** - Microservices framework
- **Maven 3.8+** - Build tool

### Infrastructure
- **Eureka Server** - Service discovery
- **Spring Cloud Gateway** - API Gateway with load balancing
- **Redis** - Caching layer (5-min TTL)
- **Apache Kafka** - Event-driven messaging
- **PostgreSQL** - Persistent storage

### External APIs
- **FlightAware AeroAPI** - Real-time flight data
- **OpenAI API** - GPT-3.5-turbo for summaries

### Testing
- **JUnit 5** - Unit testing framework
- **Mockito** - Mocking framework
- **WireMock** - HTTP API mocking
- **Testcontainers** - Integration testing
- **Embedded Redis/Kafka** - Lightweight testing

---

## 🎯 Key Features

- ✅ **Event-Driven Architecture** - Kafka for async processing
- ✅ **Sub-100ms Cache** - Redis with 5-minute TTL
- ✅ **Service Discovery** - Eureka-based registration
- ✅ **AI Summaries** - OpenAI GPT-3.5-turbo integration
- ✅ **Load Balancing** - Client-side with Spring Cloud
- ✅ **Health Checks** - Spring Actuator endpoints
- ✅ **40/40 Tests Passing** - TDD approach with >90% coverage

---

## 📡 API Documentation

### 1. Get Flight Data
```http
GET /api/v1/flight/{ident}
```

**Example Request:**
```bash
curl http://localhost:8080/api/v1/flight/UAL123
```

**Response (200 OK):**
```json
{
  "ident": "UAL123",
  "fa_flight_id": "UAL123-1234567890-1-0",
  "status": "En-Route / In Flight",
  "aircraft_type": "B738",
  "origin": "KORD",
  "destination": "KLAX",
  "scheduled_out": "2025-11-18T12:00:00Z",
  "actual_out": "2025-11-18T12:05:00Z",
  "latitude": 39.8,
  "longitude": -98.6,
  "altitude": 35000,
  "groundspeed": 450
}
```

**Error Responses:**
- `404 Not Found` - Flight doesn't exist
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - API failure

### 2. Get AI-Generated Summary
```http
GET /api/v1/flight/{ident}/summary
```

**Example Request:**
```bash
curl http://localhost:8080/api/v1/flight/UAL123/summary
```

**Response (200 OK):**
```json
{
  "ident": "UAL123",
  "fa_flight_id": "UAL123-1234567890-1-0",
  "summary_text": "United Flight 123 is currently en route from Chicago O'Hare to Los Angeles. The flight departed 5 minutes late at 12:05 PM and is expected to arrive on time.",
  "generated_at": "2025-11-18T12:06:00Z"
}
```

**Full API Specification:** [docs/API-SPEC.yml](docs/API-SPEC.yml)

---

## 🔐 Configuration

**Required API Keys** (add to `.env` file):
```bash
FLIGHTAWARE_API_KEY=your_key_here  # Get from flightaware.com
OPENAI_API_KEY=your_key_here       # Get from platform.openai.com
POSTGRES_PASSWORD=secure_password  # Choose strong password
```

**See [.env.example](.env.example) for full configuration**

---

## ⚡ Performance Benchmarks

**Actual results from Phase 5 E2E tests:**

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| **Cache Hit Latency** | < 500ms | **31ms** | ✅ 1,838% faster |
| **Cache Miss Latency** | < 2,000ms | **31ms** | ✅ 6,445% faster |
| **Kafka Processing** | < 2s | **< 1s** | ✅ 100% faster |
| **E2E Flow** | < 10s | **~4s** | ✅ 150% faster |
| **LLM Summary Generation** | < 5s | **2-3s** | ✅ 40-60% faster |

**Test Results:** 5/5 E2E tests passed | 0 failures | 13.584s total execution time

**Source:** [integration-tests/PHASE5_TEST_RESULTS.md](integration-tests/PHASE5_TEST_RESULTS.md)

---

## 🔧 Troubleshooting

### Issue 1: Services Won't Start

**Symptoms:** `docker-compose up` fails or services show as "unhealthy"

**Solution:**
```bash
# Check Docker daemon
docker info

# Check system resources
df -h  # Disk space
free -h  # Memory

# Clean and rebuild
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

### Issue 2: Redis Connection Errors

**Symptoms:** "Connection refused" errors, all requests hitting external API

**Solution:**
```bash
# Check Redis status
docker-compose ps redis
docker exec prod-redis redis-cli ping

# Restart Redis
docker-compose restart redis

# Clear cache if needed
docker exec prod-redis redis-cli FLUSHALL
```

### Issue 3: Kafka Consumer Not Processing

**Symptoms:** Summaries not being generated, high consumer lag

**Solution:**
```bash
# Check Kafka status
docker exec prod-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check consumer group
docker exec prod-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group llm-summary-consumer-group

# Restart LLM Summary Service
docker-compose restart llm-summary-service
```

### Issue 4: Database Connection Pool Exhausted

**Symptoms:** "Connection pool exhausted" errors, slow queries

**Solution:**
```bash
# Check active connections
docker exec prod-postgres psql -U airline_tracker_user -d airline_tracker -c "SELECT count(*) FROM pg_stat_activity;"

# Restart application services
docker-compose restart flightdata-service llm-summary-service
```

### Issue 5: High Memory Usage

**Symptoms:** Services crashing with OutOfMemoryError

**Solution:**
```bash
# Check memory usage
docker stats

# Increase memory limits in docker-compose.yml
# Edit: deploy.resources.limits.memory: 2G

# Restart with new limits
docker-compose up -d
```

**More Help:** See [docs/RUNBOOK.md](docs/RUNBOOK.md) for detailed operational procedures

---

## 🤝 Contributing

We welcome contributions! Here's how to get started:

### Development Setup

```bash
# 1. Fork and clone
git clone https://github.com/YOUR_USERNAME/Airline-Tracking-System.git
cd airline-tracker-system

# 2. Create feature branch
git checkout -b feature/your-feature-name

# 3. Make changes and test
mvn clean test  # Run all tests
mvn verify      # Run integration tests

# 4. Commit with conventional commits
git commit -m "feat: add new feature"
git commit -m "fix: resolve bug"
git commit -m "docs: update README"
```

### Code Standards

- **Java 17** with Spring Boot 3.2
- **Test-Driven Development (TDD)** - Write tests first
- **SOLID Principles** - Clean, maintainable code
- **Test Coverage** - Maintain >90% coverage
- **Documentation** - Update docs for new features

### Pull Request Process

1. **Update tests** - All tests must pass
2. **Update documentation** - README, API specs, etc.
3. **Follow code style** - Use existing patterns
4. **Add changelog entry** - Describe your changes
5. **Request review** - Tag maintainers

### Running Tests

```bash
# Unit tests only
mvn test

# Integration tests
mvn verify

# E2E tests
cd integration-tests
mvn test
```

### Reporting Issues

- Use GitHub Issues
- Include error logs
- Provide reproduction steps
- Specify environment details

**Code of Conduct:** Be respectful, collaborative, and constructive

---

## 📚 Documentation

| Document | Description |
|----------|-------------|
| **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** | System design & diagrams |
| **[API-SPEC.yml](docs/API-SPEC.yml)** | OpenAPI 3.0 specification |
| **[RUNBOOK.md](docs/RUNBOOK.md)** | Operational procedures |
| **[PROJECT_COMPLETION_REPORT.md](docs/PROJECT_COMPLETION_REPORT.md)** | Final project report |
| **[DEPLOYMENT.md](DEPLOYMENT.md)** | Deployment guide |
| **[QUICK-START.md](QUICK-START.md)** | 60-second setup |

---

## 📝 License

MIT License - See [LICENSE](LICENSE) for details

This is a demo project showcasing microservices architecture, event-driven design, and AI integration.

---

## 🎉 Acknowledgments

Built with:
- **Spring Boot & Spring Cloud** - Microservices framework
- **FlightAware AeroAPI** - Real-time flight data
- **OpenAI GPT-3.5-turbo** - AI-powered summaries
- **Redis, Kafka, PostgreSQL** - Infrastructure components

---

**Status**: ✅ **PRODUCTION READY**  
**Code**: 13,319 lines | **Tests**: 40/40 passing | **Performance**: Sub-100ms cache  
**Last Updated**: November 18, 2025
