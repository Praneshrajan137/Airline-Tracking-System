# Airline Tracking System - Product Requirements Document

## 1. Overview

### Project Information

- **Project Name:** Project-1: AI-Native Airline Tracking System
- **Version:** 1.0.0
- **Date:** 2025-11-10
- **Document Owner:** [Your Name]
- **Status:** Draft → In Review → Approved

### Purpose

A Java-based microservice application to track real-time flight status and provide AI-generated natural-language summaries for end users.

### Goals

1. **Demonstrate Technical Excellence:**
   - Java 17 + Spring Boot 3.x microservices architecture
   - Real-time data integration with external APIs
   - Event-driven architecture with Kafka
   - Intelligent caching strategies

2. **Business Value:**
   - Provide instant flight status information to users
   - Translate complex flight data into human-readable summaries
   - Reduce cognitive load for non-technical users

3. **System Design Showcase:**
   - Microservices with clear separation of concerns
   - Distributed system patterns (caching, async messaging)
   - Integration with third-party APIs (FlightAware, OpenAI)
   - Production-ready deployment pipeline

### Success Criteria

- ✅ System responds to flight queries within 500ms (cached)
- ✅ AI summaries generate within 2 seconds
- ✅ 99.9% uptime during business hours
- ✅ Zero hardcoded secrets in codebase
- ✅ >90% test coverage across all services
- ✅ Successfully handles 100 concurrent users

## 2. User Stories & Acceptance Criteria

### US-101: Flight Search (Core Capability)

**Priority:** P0 (Critical)

**Story Points:** 8

**User Story:**

```
As a traveler or travel agent
I want to search for a flight by its flight number (e.g., "UAL123")
So that I can retrieve its detailed, real-time flight status
```

**Acceptance Criteria:**

1. **Input Validation:**
   - ✅ Must accept flight ident in format: [AIRLINE_CODE][FLIGHT_NUMBER]
   - ✅ Format specification:
     - Airline code: 2-3 uppercase letters (e.g., "UA", "UAL", "BA")
     - Flight number: 1-4 digits (e.g., "123", "1234")
     - Maximum length: 7 characters total
     - Regex pattern: `^[A-Z]{2,3}\d{1,4}$` (case-insensitive matching)
   - ✅ Examples: "UAL123", "AA456", "DL789", "BA1234", "SW1234"
   - ✅ Must reject invalid formats:
     - Numbers only: "123"
     - Letters only: "UAL"
     - Hyphens: "UAL-123"
     - Special characters: "UAL@123"
     - Too long: "UAL12345"
     - Whitespace: "UAL 123"
   - ✅ Must handle case-insensitive input (e.g., "ual123" → "UAL123")
   - ✅ Must trim leading/trailing whitespace before validation

2. **Response Structure:**
   - ✅ Must return structured JSON object with:
     - Flight identification (fa_flight_id, ident)
     - Status (e.g., "Scheduled", "En-Route", "Landed", "Cancelled")
     - Origin and destination airport codes (ICAO format)
     - Scheduled and actual departure/arrival times (ISO 8601)
     - Aircraft information (type, registration if available)
     - Real-time position (latitude, longitude, altitude, groundspeed)

3. **Error Handling:**
   - ✅ Must return 404 with clear message if flight not found
   - ✅ Must return 400 if flight ident is invalid format
   - ✅ Must return 500 if upstream API fails (with retry logic)
   - ✅ Must return 429 if rate limit exceeded

4. **Performance:**
   - ✅ Must respond within 500ms when data is cached
   - ✅ Must respond within 2000ms on cache miss (upstream API call)

**Definition of Done:**

- [ ] Unit tests written and passing
- [ ] Integration tests with mocked FlightAware API
- [ ] API documentation updated
- [ ] Performance benchmarks met
- [ ] Code reviewed and approved

---

### US-102: LLM Summary Generation

**Priority:** P0 (Critical)

**Story Points:** 13

**User Story:**

```
As a user
I want to request a "plain-English" summary of a flight's status
So that I can quickly understand the most important information without reading the full JSON
```

**Acceptance Criteria:**

1. **Input:**
   - ✅ Must accept same flight ident as US-101
   - ✅ Must validate flight exists before generating summary

2. **Output Structure:**
   - ✅ Must return a single string of natural-language text
   - ✅ Summary must be 2-3 sentences maximum
   - ✅ Summary must include:
     - Flight number and airline
     - Current status (On Time, Delayed, En Route, Landed)
     - Origin and destination city names (not just codes)
     - Departure and arrival times in human-readable format
     - Special notes (e.g., "departed late", "diverted")

3. **Example Output:**

```
   "United Flight 123 (UAL123) is currently En Route from Chicago to Los Angeles. 
   The flight departed 5 minutes late at 12:05 PM CST and is expected to arrive 
   on time at 2:30 PM PST."
```

4. **Error Handling:**
   - ✅ Must return 404 if flight summary not yet generated
   - ✅ Must return 404 if flight doesn't exist
   - ✅ Must handle LLM API failures gracefully (retry with backoff)
   - ✅ Must not reveal LLM errors to end users

5. **Performance:**
   - ✅ Must return summary within 2000ms
   - ✅ Summary generation workflow:
     - **Trigger:** On first flight lookup (cache miss) in flightdata-service
     - **Process:** 
       1. flightdata-service retrieves flight data from FlightAware API
       2. flightdata-service publishes Kafka event `flight-data-received` with flight data
       3. llm-summary-service consumes event asynchronously
       4. llm-summary-service generates summary via OpenAI API
       5. llm-summary-service stores summary in PostgreSQL database
     - **Fallback:** If Kafka unavailable, generate synchronously with 5-second timeout
     - **Retry:** Failed summary generation retries up to 3 times with exponential backoff

**Definition of Done:**

- [ ] LLM prompt template tested with sample data
- [ ] Kafka event consumer implemented and tested
- [ ] Database schema created for storing summaries
- [ ] End-to-end test: flight search → event → summary generation
- [ ] Error scenarios tested (API failures, rate limits)

## 3. Functional Requirements (FRs)

### FR-1: Third-Party Flight Data Integration

**Requirement:** System must integrate with FlightAware AeroAPI to retrieve real-time flight information.

**Details:**

- **API:** FlightAware AeroAPI (https://aeroapi.flightaware.com/aeroapi)
- **Endpoint:** GET /flights/{ident}
- **Authentication:** x-apikey header with API key from environment variable
- **Rate Limits:** 
  - Free tier: ~$5/month (~500 queries)
  - Must implement caching to respect limits
- **Response Format:** JSON
- **Timeout:** 5 seconds max
- **Retry Logic:** 3 attempts with exponential backoff

**Acceptance:**

- ✅ Successfully retrieves flight data for valid ident
- ✅ Handles API errors gracefully (404, 500, timeout)
- ✅ Implements circuit breaker pattern for repeated failures
- ✅ Logs all API calls for monitoring

---

### FR-2: Intelligent Data Caching

**Requirement:** System must cache responses from FlightAware API to reduce latency and respect rate limits.

**Details:**

- **Cache Technology:** Redis (in-memory key-value store)
- **Cache Pattern:** Cache-Aside (Lazy Loading)
- **Cache Key Format:** `flight:{ident}` (e.g., `flight:UAL123`)
- **TTL (Time To Live):** 5 minutes (300 seconds)
- **Cache Miss Behavior:**
  1. Check Redis cache
  2. If miss, call FlightAware API
  3. Store result in cache with TTL
  4. Return result to user

**Cache Fallback Strategy:**

- **If Redis is down but FlightAware is up:**
  - Bypass cache, call FlightAware API directly
  - Log cache failure for monitoring
  - Return flight data to user (may exceed 500ms target)

- **If both Redis and FlightAware are down:**
  - Return 503 Service Unavailable
  - Include error message: "Flight data service temporarily unavailable"

- **If Redis returns stale data (TTL expired but API call fails):**
  - Serve stale cache data with warning header: `X-Cache-Status: stale`
  - Log cache staleness for monitoring

**Acceptance:**

- ✅ First request for flight hits FlightAware API
- ✅ Subsequent requests within 5 minutes hit cache (< 500ms)
- ✅ After TTL expires, cache is refreshed
- ✅ Cache invalidation works correctly
- ✅ Fallback to direct API call when Redis unavailable
- ✅ Graceful degradation when both cache and API unavailable

---

### FR-3: LLM Integration for Summaries

**Requirement:** System must send structured flight data to an LLM API to generate natural-language summaries.

**Details:**

- **LLM Provider:** OpenAI (GPT-3.5-turbo or GPT-4)
- **API Endpoint:** POST https://api.openai.com/v1/chat/completions
- **Authentication:** Bearer token from environment variable
- **Model Configuration:**
  - Model: gpt-3.5-turbo (cost-effective)
  - Max Tokens: 150
  - Temperature: 0.3 (deterministic, factual)
- **Prompt Template:** See docs/LLM-PROMPT-TEMPLATE.md
- **Rate Limits:**
  - Free tier: 3 requests/minute
  - Paid tier: 3,500 requests/minute
- **Error Handling:** Exponential backoff on rate limit (429)

**Acceptance:**

- ✅ Generates accurate, concise summaries from flight data
- ✅ Handles LLM API errors gracefully
- ✅ Implements retry logic with backoff
- ✅ Logs token usage for cost monitoring

---

### FR-4: Microservices Architecture

**Requirement:** System must be built using microservice architecture with clear separation of concerns.

**Details:**

- **Service Registry:** Eureka (Spring Cloud Netflix)
- **API Gateway:** Spring Cloud Gateway (single entry point)
- **Backend Services:**
  1. **flightdata-service:** Handles flight data retrieval, caching, and event publishing
  2. **llm-summary-service:** Consumes events, generates summaries, stores in database

- **Communication Patterns:**
  - **Synchronous:** HTTP/REST via API Gateway
  - **Asynchronous:** Kafka for event-driven communication

**Kafka Event Schema:**

- **Topic Name:** `flight-data-events`
- **Partitions:** 3 (for scalability)
- **Replication Factor:** 2 (for high availability)
- **Event Type:** `flight-data-received`
- **Event Schema (JSON):**

```json
{
  "eventId": "uuid-v4",
  "eventType": "flight-data-received",
  "timestamp": "2025-11-10T10:30:00Z",
  "flightIdent": "UAL123",
  "flightData": {
    "fa_flight_id": "UAL123-1234567890-abcd1234",
    "ident": "UAL123",
    "status": "En Route",
    "origin": {
      "code": "KORD",
      "city": "Chicago",
      "name": "Chicago O'Hare International Airport"
    },
    "destination": {
      "code": "KLAX",
      "city": "Los Angeles",
      "name": "Los Angeles International Airport"
    },
    "departure": {
      "scheduled": "2025-11-10T12:00:00Z",
      "actual": "2025-11-10T12:05:00Z"
    },
    "arrival": {
      "scheduled": "2025-11-10T14:30:00Z",
      "estimated": "2025-11-10T14:30:00Z"
    },
    "aircraft": {
      "type": "Boeing 737-800",
      "registration": "N12345"
    },
    "position": {
      "latitude": 39.1234,
      "longitude": -104.5678,
      "altitude": 35000,
      "groundspeed": 450
    }
  }
}
```

- **Consumer Group:** `llm-summary-service-group`
- **Dead Letter Queue:** `flight-data-events-dlq` (for failed processing)
- **Retry Policy:** 3 attempts with exponential backoff (1s, 2s, 4s)

**Acceptance:**

- ✅ All services register with Eureka on startup
- ✅ API Gateway routes requests to appropriate service
- ✅ Services can be scaled independently
- ✅ Failure of one service doesn't cascade to others
- ✅ Kafka events published successfully on flight data retrieval
- ✅ Dead letter queue handles failed message processing

## 4. Non-Functional Requirements (NFRs)

### NFR-1: Technology Stack

**Requirement:** Must be built with Java 17 and Spring Boot 3.x framework.

**Mandated Technologies:**

- **Language:** Java 17 (LTS)
- **Framework:** Spring Boot 3.2.x
- **Build Tool:** Maven 3.8+
- **Cloud Platform:** Spring Cloud 2023.x
- **Testing:** JUnit 5, Mockito, Testcontainers

**Justification:** Enterprise-grade, production-ready stack with strong community support.

---

### NFR-2: Code Quality Standards

**Requirement:** Must adhere to SOLID Principles and Clean Code best practices.

**Standards:**

- **SOLID Principles:** 
  - Single Responsibility
  - Open/Closed
  - Liskov Substitution
  - Interface Segregation
  - Dependency Inversion

- **Clean Code:**
  - Functions < 20 lines
  - Clear, descriptive names (no abbreviations)
  - No magic numbers or strings
  - Comments explain WHY, not WHAT
  - DRY (Don't Repeat Yourself)

- **Metrics:**
  - Cyclomatic Complexity < 10 per method
  - Code duplication < 5%
  - Test coverage > 90%

---

### NFR-3: Resilience & Error Handling

**Requirement:** Must implement robust error handling and graceful degradation.

**Error Handling Strategy:**

- **HTTP Status Codes:**
  - 200: Success
  - 400: Bad Request (invalid input)
  - 404: Not Found (flight doesn't exist)
  - 429: Too Many Requests (rate limit)
  - 500: Internal Server Error (unexpected failure)
  - 503: Service Unavailable (dependency down)

- **Retry Logic:**
  - 3 attempts with exponential backoff
  - Circuit breaker after 5 consecutive failures
  - Timeout after 5 seconds

- **Error Responses:**

```json
  {
    "timestamp": "2024-11-10T10:30:00Z",
    "status": 404,
    "error": "Not Found",
    "message": "Flight UAL123 not found",
    "path": "/api/v1/flight/UAL123"
  }
```

---

### NFR-4: Observability

**Requirement:** All services must be "Observable by Default" with structured logging and metrics.

**Logging Requirements:**

- **Log Levels:** DEBUG, INFO, WARN, ERROR
- **Log Format:** JSON (structured logging)
- **Log Content:**
  - Timestamp (ISO 8601)
  - Service name
  - Trace ID (for distributed tracing)
  - Request details (endpoint, method, params)
  - Response details (status, duration)
  - Error details (stack trace, context)

**Metrics:**

- Request rate (requests/second)
- Error rate (errors/requests)
- Response time (p50, p95, p99)
- Cache hit rate
- Kafka lag

**Metrics Collection:**

- **Technology:** Micrometer with Prometheus format
- **Endpoint:** `/actuator/prometheus` on each service
- **Scraping:** Prometheus scrapes metrics every 15 seconds
- **Dashboards:** Grafana dashboards for visualization
- **Alerting:** AlertManager with thresholds:
  - Error rate > 5% for 5 minutes
  - Response time p95 > 2000ms for 5 minutes
  - Cache hit rate < 80% for 10 minutes
  - Kafka consumer lag > 1000 messages

**Log Aggregation:**

- **Technology:** ELK Stack (Elasticsearch, Logstash, Kibana) or CloudWatch
- **Log Shipping:** Filebeat or CloudWatch Logs Agent
- **Retention:** 30 days for INFO/WARN, 90 days for ERROR
- **Search:** Full-text search by trace ID, service name, error type

---

### NFR-5: Performance Targets

**Requirement:** System must meet strict performance benchmarks.

**Targets:**

- **GET /flight/{ident}:**
  - With cache: < 500ms (p95)
  - Without cache: < 2000ms (p95)

- **GET /flight/{ident}/summary:**
  - Pre-generated: < 2000ms (p95)

- **Throughput:**
  - 100 concurrent users
  - 1000 requests/minute sustained

**Load Testing:**

- Use JMeter or Gatling
- Simulate realistic traffic patterns
- Monitor resource usage (CPU, memory, network)

---

### NFR-6: Security

**Requirement:** All sensitive data must be protected and never exposed in code or logs.

**Security Requirements:**

- **Secrets Management:**
  - All API keys in environment variables
  - No secrets in Git repository
  - Use .env.example with placeholders only

- **Input Validation:**
  - Validate all user inputs
  - Sanitize inputs to prevent injection attacks
  - Use @Valid and Bean Validation

- **API Security:**
  - CORS configured properly
  - Rate limiting enabled (see details below)
  - HTTPS enforced in production

**Rate Limiting Details:**

- **Algorithm:** Token bucket
- **Scope:** Per IP address
- **Limits:**
  - GET /api/v1/flight/{ident}: 100 requests/minute per IP
  - GET /api/v1/flight/{ident}/summary: 50 requests/minute per IP
- **Response:** 
  - HTTP 429 Too Many Requests
  - Header: `Retry-After: 60` (seconds)
  - Body: `{"error": "Rate limit exceeded", "retryAfter": 60}`
- **Implementation:** Spring Cloud Gateway rate limiter (Redis-backed)
- **Bypass:** Internal service-to-service calls not rate limited

- **Logging:**
  - Never log sensitive data (API keys, tokens)
  - Mask PII in logs if present

**Acceptance:**

- ✅ Security scan passes (no critical vulnerabilities)
- ✅ No secrets found in Git history
- ✅ All endpoints validate inputs

---

## 5. API Specifications

### 5.1 API Endpoints

**Base URL:** `https://api.airline-tracker.com/api/v1`

**API Versioning:** URL-based versioning (`/api/v1/`). Future versions will use `/api/v2/`, etc.

#### GET /api/v1/flight/{ident}

**Description:** Retrieve real-time flight status by flight identifier.

**Path Parameters:**
- `ident` (string, required): Flight identifier (e.g., "UAL123")
  - Format: 2-3 letters followed by 1-4 digits
  - Case-insensitive
  - Max length: 7 characters

**Query Parameters:** None

**Request Headers:**
- `Accept: application/json`

**Response 200 OK:**

```json
{
  "fa_flight_id": "UAL123-1234567890-abcd1234",
  "ident": "UAL123",
  "status": "En Route",
  "origin": {
    "code": "KORD",
    "city": "Chicago",
    "name": "Chicago O'Hare International Airport",
    "timezone": "America/Chicago"
  },
  "destination": {
    "code": "KLAX",
    "city": "Los Angeles",
    "name": "Los Angeles International Airport",
    "timezone": "America/Los_Angeles"
  },
  "departure": {
    "scheduled": "2025-11-10T12:00:00Z",
    "actual": "2025-11-10T12:05:00Z",
    "gate": "C12",
    "terminal": "Terminal 1"
  },
  "arrival": {
    "scheduled": "2025-11-10T14:30:00Z",
    "estimated": "2025-11-10T14:30:00Z",
    "gate": "A5",
    "terminal": "Terminal 2"
  },
  "aircraft": {
    "type": "Boeing 737-800",
    "registration": "N12345"
  },
  "position": {
    "latitude": 39.1234,
    "longitude": -104.5678,
    "altitude": 35000,
    "groundspeed": 450,
    "heading": 270
  },
  "lastUpdated": "2025-11-10T12:30:00Z"
}
```

**Response 400 Bad Request:**

```json
{
  "timestamp": "2025-11-10T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid flight identifier format. Expected format: [AIRLINE_CODE][FLIGHT_NUMBER] (e.g., UAL123)",
  "path": "/api/v1/flight/invalid123"
}
```

**Response 404 Not Found:**

```json
{
  "timestamp": "2025-11-10T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Flight UAL123 not found",
  "path": "/api/v1/flight/UAL123"
}
```

**Response 429 Too Many Requests:**

```json
{
  "timestamp": "2025-11-10T10:30:00Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Maximum 100 requests per minute.",
  "retryAfter": 60,
  "path": "/api/v1/flight/UAL123"
}
```

#### GET /api/v1/flight/{ident}/summary

**Description:** Retrieve AI-generated natural-language summary of flight status.

**Path Parameters:**
- `ident` (string, required): Flight identifier (same format as above)

**Query Parameters:** None

**Request Headers:**
- `Accept: application/json`

**Response 200 OK:**

```json
{
  "flightIdent": "UAL123",
  "summary": "United Flight 123 (UAL123) is currently En Route from Chicago to Los Angeles. The flight departed 5 minutes late at 12:05 PM CST and is expected to arrive on time at 2:30 PM PST.",
  "generatedAt": "2025-11-10T12:06:00Z",
  "source": "openai-gpt-3.5-turbo"
}
```

**Response 404 Not Found:**

```json
{
  "timestamp": "2025-11-10T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Summary for flight UAL123 not yet available. Please try again in a few moments.",
  "path": "/api/v1/flight/UAL123/summary"
}
```

### 5.2 API Gateway Routing

**Routes:**
- `/api/v1/flight/**` → `flightdata-service`
- `/api/v1/flight/**/summary` → `llm-summary-service`
- `/actuator/**` → Direct service access (health checks, metrics)

**Load Balancing:** Round-robin across service instances registered in Eureka

---

## 6. Database Schema

### 6.1 PostgreSQL Database: `airline_tracker`

**Database:** PostgreSQL 15+

**Schema:** `public`

#### Table: `flight_summaries`

**Purpose:** Store AI-generated flight summaries.

**Schema:**

```sql
CREATE TABLE flight_summaries (
    id BIGSERIAL PRIMARY KEY,
    flight_ident VARCHAR(7) NOT NULL,
    summary TEXT NOT NULL,
    source_model VARCHAR(50) NOT NULL DEFAULT 'openai-gpt-3.5-turbo',
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_flight_ident UNIQUE (flight_ident)
);

CREATE INDEX idx_flight_summaries_flight_ident ON flight_summaries(flight_ident);
CREATE INDEX idx_flight_summaries_generated_at ON flight_summaries(generated_at);
```

**Fields:**
- `id`: Primary key (auto-increment)
- `flight_ident`: Flight identifier (e.g., "UAL123") - unique constraint
- `summary`: AI-generated summary text
- `source_model`: LLM model used (e.g., "openai-gpt-3.5-turbo")
- `generated_at`: When summary was generated
- `created_at`: Record creation timestamp
- `updated_at`: Last update timestamp (for future updates)

**Data Retention:** Summaries retained for 90 days, then archived

**Migration Strategy:** Flyway for schema versioning

---

## 7. Environment Variables

### 7.1 Required Environment Variables

All services require the following environment variables:

#### Service Registry (Eureka)

```bash
# Eureka Server
EUREKA_SERVER_PORT=8761
EUREKA_INSTANCE_HOSTNAME=localhost
EUREKA_CLIENT_REGISTER_WITH_EUREKA=true
EUREKA_CLIENT_FETCH_REGISTRY=true
```

#### API Gateway

```bash
# API Gateway
SERVER_PORT=8080
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://localhost:8761/eureka/
SPRING_CLOUD_GATEWAY_ROUTES_FLIGHTDATA_SERVICE_URI=http://flightdata-service
SPRING_CLOUD_GATEWAY_ROUTES_LLM_SUMMARY_SERVICE_URI=http://llm-summary-service
```

#### Flight Data Service

```bash
# Flight Data Service
SERVER_PORT=8081
SPRING_APPLICATION_NAME=flightdata-service
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://localhost:8761/eureka/

# FlightAware API
FLIGHTAWARE_API_KEY=your_flightaware_api_key_here
FLIGHTAWARE_API_BASE_URL=https://aeroapi.flightaware.com/aeroapi
FLIGHTAWARE_API_TIMEOUT_SECONDS=5
FLIGHTAWARE_API_RETRY_ATTEMPTS=3

# Redis Cache
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=
REDIS_CACHE_TTL_SECONDS=300

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_KAFKA_PRODUCER_TOPIC=flight-data-events
```

#### LLM Summary Service

```bash
# LLM Summary Service
SERVER_PORT=8082
SPRING_APPLICATION_NAME=llm-summary-service
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://localhost:8761/eureka/

# OpenAI API
OPENAI_API_KEY=your_openai_api_key_here
OPENAI_API_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-3.5-turbo
OPENAI_MAX_TOKENS=150
OPENAI_TEMPERATURE=0.3
OPENAI_TIMEOUT_SECONDS=10
OPENAI_RETRY_ATTEMPTS=3

# PostgreSQL Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/airline_tracker
SPRING_DATASOURCE_USERNAME=airline_tracker_user
SPRING_DATASOURCE_PASSWORD=your_database_password_here
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false

# Kafka Consumer
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
SPRING_KAFKA_CONSUMER_GROUP_ID=llm-summary-service-group
SPRING_KAFKA_CONSUMER_TOPIC=flight-data-events
SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET=earliest
```

### 7.2 Environment Variable Validation

- All required variables must be set on service startup
- Missing variables cause application startup failure with clear error message
- Use Spring Boot's `@ConfigurationProperties` for type-safe configuration
- Validate API keys format (if applicable) on startup

---

## 8. Deployment

### 8.1 Local Development Setup

**Prerequisites:**
- Java 17 JDK
- Maven 3.8+
- Docker Desktop (for Redis, Kafka, PostgreSQL)

**Docker Compose Services:**

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: airline_tracker
      POSTGRES_USER: airline_tracker_user
      POSTGRES_PASSWORD: dev_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

**Startup Order:**
1. Infrastructure: PostgreSQL, Redis, Kafka (via Docker Compose)
2. Service Registry (Eureka) - Port 8761
3. Flight Data Service - Port 8081
4. LLM Summary Service - Port 8082
5. API Gateway - Port 8080

### 8.2 Production Deployment

**Containerization:**
- Each service packaged as Docker image
- Base image: `eclipse-temurin:17-jre-alpine`
- Multi-stage Maven build for optimized image size

**Orchestration:**
- Kubernetes (recommended) or Docker Swarm
- Helm charts for deployment
- Horizontal Pod Autoscaling (HPA) based on CPU/memory

**Infrastructure Requirements:**

| Service | CPU | Memory | Replicas | Storage |
|---------|-----|--------|----------|---------|
| service-registry | 0.5 | 512Mi | 2 | N/A |
| api-gateway | 1 | 1Gi | 2 | N/A |
| flightdata-service | 1 | 1Gi | 3 | N/A |
| llm-summary-service | 2 | 2Gi | 3 | N/A |
| PostgreSQL | 2 | 4Gi | 1 (primary) | 100Gi |
| Redis | 1 | 2Gi | 2 (sentinel) | N/A |
| Kafka | 2 | 4Gi | 3 | 200Gi |

**Health Checks:**
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`
- Startup: `/actuator/health/startup`

**CI/CD Pipeline:**
- Build: Maven build + Docker image creation
- Test: Unit tests, integration tests, security scans
- Deploy: Blue-green deployment strategy
- Rollback: Automatic on health check failures

---

## 9. Out of Scope (for MVP)

**Not included in initial release:**

- ❌ User authentication/authorization
- ❌ Multi-language support (i18n)
- ❌ Flight booking capabilities
- ❌ Push notifications for flight updates
- ❌ Historical flight data analysis
- ❌ Mobile applications
- ❌ Admin dashboard
- ❌ Payment processing

**May be added in future versions.**

---

## 10. Dependencies & Assumptions

### External Dependencies

1. **FlightAware AeroAPI:**
   - Assumption: API will be available 99.9% of the time
   - Fallback: Cache serves stale data if API is down

2. **OpenAI API:**
   - Assumption: GPT-3.5-turbo will remain available
   - Fallback: Return raw flight data if LLM fails

3. **Infrastructure:**
   - Redis, Kafka, PostgreSQL must be available
   - Assumption: Cloud provider (AWS/Heroku) meets SLA

### Technical Assumptions

- Users have internet connectivity
- Modern browsers support JSON responses
- Development team is familiar with Spring Boot
- Adequate API credits for FlightAware and OpenAI

---

## 11. Risks & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| FlightAware API rate limits exceeded | Medium | High | Implement aggressive caching; upgrade to paid tier |
| OpenAI API cost overruns | Medium | Medium | Monitor token usage; set budget alerts; use GPT-3.5 instead of GPT-4 |
| Redis cache failure | Low | High | Implement fallback to direct API calls; Redis clustering |
| Kafka message loss | Low | Medium | Use persistent topics; implement dead-letter queue |
| Security vulnerability | Low | Critical | Regular security scans; dependency updates; code reviews |

---

## 12. Approval & Sign-Off

| Stakeholder | Role | Signature | Date |
|-------------|------|-----------|------|
| [Your Name] | Product Owner | __________ | ______ |
| [Name] | Tech Lead | __________ | ______ |
| [Name] | QA Lead | __________ | ______ |

---

**Document Version:** 1.1.0
**Last Updated:** 2025-11-10
**Next Review:** 2025-12-10

**Changelog:**
- v1.1.0 (2025-11-10): Added API specifications, database schema, Kafka event schema, environment variables, deployment details, enhanced input validation, cache fallback strategy, rate limiting details, and monitoring specifications
- v1.0.0 (2025-11-10): Initial PRD draft

