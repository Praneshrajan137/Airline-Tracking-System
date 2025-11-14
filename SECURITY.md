# Security & API Protection Guide

## 🔒 Overview

This document describes the security measures implemented to protect your API keys and prevent cost overruns, specifically for your **$5 FlightAware free tier**.

## 🛡️ Rate Limiting Protection

### FlightAware API (Free Tier Protection)

**Your Free Tier:** 10,000 API calls/month (~$5 value)

**Implemented Limits:**
- **10 calls/minute** - Prevents burst abuse
- **200 calls/hour** - 15% of daily budget
- **300 calls/day** - Conservative 10% of monthly limit

**Why These Limits?**
- Protects against accidental loops or DOS attacks
- Leaves 70% buffer for legitimate traffic spikes
- Daily limit: 300 calls/day × 30 days = 9,000 calls/month (90% utilization)

### OpenAI API (Cost Control)

**Pricing:** GPT-3.5-turbo ~$0.002 per 1,000 tokens (~150 tokens/call)

**Implemented Limits:**
- **20 calls/minute** - Prevents rapid bursts
- **500 calls/hour** - Reasonable hourly usage
- **5,000 calls/day** - Daily cap

**Cost Estimate at Max Usage:**
- 5,000 calls/day × 30 days = 150,000 calls/month
- 150,000 calls × 150 tokens/call = 22.5M tokens
- 22.5M tokens × $0.002/1K = **~$45/month maximum**

## 🚨 How Rate Limiting Works

### 1. Pre-Flight Check
Before EVERY API call, the system checks:
```
if (calls_this_minute > 10) → BLOCK
if (calls_this_hour > 200) → BLOCK  
if (calls_this_day > 300) → BLOCK
```

### 2. Distributed Tracking
- Uses **Redis** as central counter
- Works across multiple service instances
- Atomic increment operations prevent race conditions

### 3. Auto-Reset Windows
- Minute counter: Resets every 60 seconds
- Hour counter: Resets every 3600 seconds
- Day counter: Resets every 86,400 seconds

### 4. Warning Thresholds
At 80% usage, warnings are logged:
```
⚠️ API usage at 80%: 240/300 calls in day window
```

## 📊 Monitoring Usage

### View Current Usage (Logs)
```bash
# Check FlightData service logs
docker-compose logs -f flightdata-service | grep "Current usage"

# Example output:
# FlightAware API call allowed - Current usage: minute=3/10, hour=45/200, day=120/300
```

### View Rate Limit Blocks (Logs)
```bash
# Check for rate limit hits
docker-compose logs flightdata-service | grep "Rate limit exceeded"

# Example output:
# ⚠️ Rate limit exceeded: 10 calls/minute
```

### Redis Inspection (Manual)
```bash
# Connect to Redis
docker exec -it prod-redis redis-cli

# Check current counters
GET ratelimit:flightaware:minute
GET ratelimit:flightaware:hour
GET ratelimit:flightaware:day
```

## 🎯 What Happens When Limit is Exceeded?

### User Experience
1. API returns **429 Too Many Requests** error
2. Error message: `"⚠️ FlightAware API rate limit exceeded! Usage: minute=10/10, hour=200/200, day=300/300 - Protecting your $5 free tier"`
3. Request is **NOT forwarded** to FlightAware (no cost incurred)

### System Behavior
```
Client Request
    ↓
API Gateway
    ↓
FlightData Service
    ↓
[RATE LIMITER] ← Checks Redis counters
    ↓
    ├─ ALLOWED → Call FlightAware API → Increment counter
    │
    └─ BLOCKED → Return 429 error → No API call made ✅
```

## 🔧 Adjusting Rate Limits

### For Development (Higher Limits)
Edit `.env` file:
```bash
# Increase for testing
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_MINUTE=50
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_HOUR=1000
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_DAY=2000
```

### For Stricter Production (Lower Limits)
```bash
# Very conservative for $5 free tier
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_MINUTE=5
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_HOUR=100
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_DAY=200
```

### Disable Rate Limiting (NOT RECOMMENDED)
```bash
FLIGHTAWARE_RATE_LIMIT_ENABLED=false
```

## 🔐 Additional Security Measures

### 1. Environment Variable Protection
- **All API keys in `.env`** (never committed to git)
- **`.env` in `.gitignore`** (protected from accidental commits)
- **Docker Compose** reads from `.env` securely

### 2. Error Message Sanitization
Production configuration hides sensitive info:
```yaml
server:
  error:
    include-message: never
    include-stacktrace: never
    include-exception: false
```

### 3. HTTP Headers (Planned)
Consider adding nginx reverse proxy with:
- `Strict-Transport-Security` (HTTPS enforcement)
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Content-Security-Policy`

### 4. API Key Rotation
**Best Practice:** Rotate keys every 90 days
1. Generate new API key in provider dashboard
2. Update `.env` file
3. Restart services: `docker-compose restart`

## 📈 Cost Projection

### FlightAware Free Tier ($5/month)
| Scenario | Daily Calls | Monthly Calls | Status |
|----------|------------|---------------|---------|
| **Current Limits** | 300 | 9,000 | ✅ 90% utilization |
| Light Usage | 100 | 3,000 | ✅ 30% utilization |
| Heavy Usage | 333 | ~10,000 | ⚠️ At limit |
| **Overage** | 400+ | 12,000+ | ❌ Exceeds free tier |

### OpenAI API (Pay-as-you-go)
| Scenario | Daily Calls | Monthly Cost | Notes |
|----------|------------|--------------|-------|
| Light (500/day) | 500 | ~$0.45 | Development |
| **Current Limit** | 5,000 | ~$4.50 | Production |
| Heavy (10K/day) | 10,000 | ~$9.00 | High traffic |

## 🚨 Emergency: Disable System

If you suspect API key abuse:

```bash
# Stop all services immediately
docker-compose down

# OR disable specific service
docker-compose stop flightdata-service

# Rotate API keys
# 1. Revoke old key in FlightAware dashboard
# 2. Generate new key
# 3. Update .env file
# 4. Restart: docker-compose up -d
```

## ✅ Security Checklist

Production deployment:
- [x] API keys in `.env` (not hardcoded)
- [x] `.env` in `.gitignore`
- [x] Rate limiting enabled
- [x] Redis-backed distributed rate limiting
- [x] Error messages sanitized
- [x] Monitoring logs configured
- [ ] HTTPS/TLS enabled (add nginx)
- [ ] Firewall rules configured
- [ ] Alerting on rate limit hits (add Prometheus/Grafana)
- [ ] Regular API key rotation (90 days)

## 📞 Monitoring Recommendations

### Daily Checks
```bash
# Check API usage
docker-compose logs --tail=100 flightdata-service | grep "Current usage"

# Check for rate limit blocks
docker-compose logs --tail=100 flightdata-service | grep "exceeded"
```

### Weekly Review
1. Review FlightAware dashboard for actual API usage
2. Review OpenAI dashboard for token consumption
3. Compare against logged usage statistics
4. Adjust rate limits if needed

### Alerts (Optional: Add Prometheus/Grafana)
- Alert when rate limit hit > 5 times/hour
- Alert when daily usage > 80%
- Alert on unexpected traffic patterns

## 🔗 Additional Resources

- **FlightAware API Docs:** https://www.flightaware.com/commercial/aeroapi/
- **OpenAI API Docs:** https://platform.openai.com/docs/api-reference
- **Redis Rate Limiting:** https://redis.io/glossary/rate-limiting/
- **OWASP Security Guide:** https://owasp.org/www-project-api-security/

## 🤝 Support

If you encounter rate limiting issues:
1. Check logs: `docker-compose logs -f flightdata-service`
2. Check Redis: `docker exec -it prod-redis redis-cli`
3. Verify `.env` configuration
4. Review this document

**Remember:** Rate limiting is protection, not a bug! 🛡️

