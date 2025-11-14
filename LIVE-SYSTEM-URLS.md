# 🚀 Airline Tracking System - Live System URLs

## ✅ System Status: ALL SERVICES HEALTHY

All 8 microservices are running and healthy!

---

## 🌐 Service URLs

### 1. **Eureka Service Registry** (Service Discovery Dashboard)
**URL:** http://localhost:8761

**What you'll see:**
- Dashboard showing all registered microservices
- Instance status, uptime, and health information
- 4 registered services: API-GATEWAY, FLIGHTDATA-SERVICE, LLM-SUMMARY-SERVICE

---

### 2. **API Gateway** (Main Entry Point)
**Base URL:** http://localhost:8080

**Health Check:**
- http://localhost:8080/actuator/health

**API Endpoints:**
- **Get Flight by Ident:** `GET http://localhost:8080/api/v1/flights/{ident}`
  - Example: http://localhost:8080/api/v1/flights/UAL1234
- **Get Flight Summary:** `GET http://localhost:8080/api/v1/summaries/{ident}`
  - Example: http://localhost:8080/api/v1/summaries/UAL1234

---

### 3. **FlightData Service** (Flight Information)
**Base URL:** http://localhost:8081

**Health Check:**
- http://localhost:8081/actuator/health

**Direct API (bypasses gateway):**
- **Get Flight:** `GET http://localhost:8081/api/v1/flights/{ident}`

**Features:**
- ✅ FlightAware API integration
- ✅ Redis caching (5-minute TTL)
- ✅ Kafka event publishing
- ✅ Rate limiting (1/min, 10/hour, 13/day)

---

### 4. **LLM Summary Service** (AI Flight Summaries)
**Base URL:** http://localhost:8082

**Health Check:**
- http://localhost:8082/actuator/health

**Direct API (bypasses gateway):**
- **Get Summary:** `GET http://localhost:8082/api/v1/summaries/{ident}`

**Features:**
- ✅ Kafka consumer for flight events
- ✅ OpenAI GPT-4o-mini integration
- ✅ PostgreSQL persistence
- ✅ Rate limiting (3/min, 10/hour, 100/day)

---

### 5. **Infrastructure Services**

#### PostgreSQL Database
- **Host:** localhost:5432
- **Database:** airline_tracker
- **User:** airline_tracker_user
- **Connection:** `jdbc:postgresql://localhost:5432/airline_tracker`

#### Redis Cache
- **Host:** localhost:6379
- **Purpose:** Flight data caching, rate limiting

#### Kafka Message Broker
- **Host:** localhost:9092
- **Topic:** flight-data-events
- **Purpose:** Event-driven architecture

---

## 🧪 Test the System

### Test 1: Get Flight Data (triggers full pipeline)
```bash
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/flights/UAL1234" -Method GET | Select-Object -ExpandProperty Content
```

**What happens:**
1. Request hits API Gateway
2. Routes to FlightData Service
3. Calls FlightAware API (with rate limiting)
4. Caches result in Redis
5. Publishes Kafka event
6. LLM Summary Service consumes event
7. Calls OpenAI API for summary
8. Saves summary to PostgreSQL

### Test 2: Get AI Summary (after Test 1)
```bash
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/summaries/UAL1234" -Method GET | Select-Object -ExpandProperty Content
```

**Expected Response:**
```json
{
  "ident": "UAL1234",
  "summary_text": "United Airlines flight UA1234...",
  "generated_at": "2025-11-12T10:30:00Z"
}
```

### Test 3: Verify Cache Hit (no API call)
```bash
# Windows PowerShell
# Run Test 1 again within 5 minutes - should be instant (< 100ms)
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/flights/UAL1234" -Method GET | Select-Object -ExpandProperty Content
```

---

## 📊 Monitoring & Management

### Docker Commands

**View all running containers:**
```bash
docker ps
```

**Check logs:**
```bash
docker logs prod-flightdata-service --tail 50
docker logs prod-llm-summary-service --tail 50
docker logs prod-api-gateway --tail 50
```

**Stop all services:**
```bash
docker-compose down
```

**Restart specific service:**
```bash
docker-compose restart prod-flightdata-service
```

---

## 🔒 Security & Cost Protection

### FlightAware API
- **Free Tier:** 500 requests/month
- **Your Limit:** 390 requests/month (13/day)
- **Rate Limit:** 1 call/min, 10 calls/hour, 13 calls/day
- **Monthly Cost:** $0 (stays within free tier)

### OpenAI API
- **Budget:** $5/month
- **Model:** GPT-4o-mini
- **Your Limit:** 3,000 summaries/month (100/day)
- **Rate Limit:** 3 calls/min, 10 calls/hour, 100 calls/day
- **Estimated Monthly Cost:** $0.45 (9% of budget)

**Both APIs are protected with Redis-backed distributed rate limiting.**

---

## 📁 Key Files

- **Environment Variables:** `.env`
- **Docker Compose:** `docker-compose.yml`
- **Deployment Guide:** `DEPLOYMENT.md`
- **Security Details:** `SECURITY.md`
- **Pricing Analysis:** `PRICING-ANALYSIS.md`
- **Quick Start:** `QUICK-START.md`

---

## 🎯 Architecture Overview

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │
       ▼
┌─────────────────────┐
│   API Gateway       │  (Port 8080)
│   Spring Cloud      │
└──────┬──────────────┘
       │
       ├──────────────────┐
       │                  │
       ▼                  ▼
┌──────────────┐   ┌──────────────────┐
│ FlightData   │   │  LLM Summary     │
│  Service     │──▶│   Service        │
│ (Port 8081)  │   │  (Port 8082)     │
└──────┬───────┘   └────────┬─────────┘
       │                    │
       ▼                    ▼
┌──────────────┐   ┌──────────────────┐
│  Redis       │   │  PostgreSQL      │
│  Cache       │   │  Database        │
└──────────────┘   └──────────────────┘
       │                    ▲
       │                    │
       ▼                    │
┌──────────────────────────┴──┐
│       Kafka Broker          │
│   (Event Streaming)         │
└─────────────────────────────┘
```

---

## 🎉 Deployment Success!

Your Airline Tracking System is now:
- ✅ **Fully deployed** with 8 microservices
- ✅ **Production-ready** with health checks
- ✅ **Cost-protected** with strict rate limiting
- ✅ **Highly scalable** with event-driven architecture
- ✅ **Redis-cached** for performance
- ✅ **AI-powered** with OpenAI GPT-4o-mini

**Next Steps:**
1. Test the APIs using the commands above
2. Monitor the Eureka dashboard
3. Check service logs for any issues
4. Review `SECURITY.md` for security best practices
5. See `DEPLOYMENT.md` for scaling and production tips

**Enjoy your world-class airline tracking system!** ✈️🌍

