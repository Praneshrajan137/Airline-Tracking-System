# API Pricing Analysis & $5 Budget Protection

## 💰 Cost Breakdown for $5 Budget (Each API)

### 🛫 FlightAware AeroAPI Pricing

#### Free Starter Plan (Your Plan)
- **Cost**: $0/month (FREE)
- **Included**: 500 requests/month
- **Rate Limit**: 5 queries per minute
- **Link**: https://www.flightaware.com/commercial/aeroapi/pricing

#### Paid Plans (Reference)
| Plan | Cost/Month | Requests | Cost per Request |
|------|------------|----------|------------------|
| Bronze | $25 | 2,500 | $0.01 |
| Silver | $50 | 5,500 | $0.009 |

#### Your $5 Budget Calculation
Since you're on the **FREE tier**, you have:
- ✅ **500 requests/month FREE**
- ✅ **Zero cost** if you stay under 500/month
- ⚠️ If you exceed 500, you'd need to upgrade to Bronze ($25)

**Conservative Daily Limit:**
- 500 requests/month ÷ 30 days = **16.6 requests/day**
- With 20% safety buffer = **13 requests/day MAX**

---

### 🤖 OpenAI API Pricing

#### GPT-3.5-Turbo (Recommended for Cost)
- **Input**: $0.50 per 1M tokens
- **Output**: $2.00 per 1M tokens
- **Link**: https://openai.com/api/pricing/

#### GPT-4o Mini (Even Cheaper)
- **Input**: $0.15 per 1M tokens
- **Output**: $0.60 per 1M tokens

#### Your $5 Budget Calculation (GPT-3.5-Turbo)

**Per Flight Summary:**
- Input tokens (flight data JSON): ~400 tokens
- Output tokens (summary text): ~150 tokens
- **Cost per summary**: $0.0005 (input) + $0.0003 (output) = **$0.0008**

**$5 Budget Allows:**
- $5 ÷ $0.0008 = **6,250 summaries**
- Per month: **6,250 summaries**
- Per day: **208 summaries**
- With 50% safety buffer: **100 summaries/day MAX**

#### If Using GPT-4o Mini (Cheaper!)
- **Cost per summary**: $0.00006 + $0.00009 = **$0.00015**
- $5 ÷ $0.00015 = **33,333 summaries**
- You'd be nearly unlimited!

---

## 🛡️ STRICT RATE LIMITS (Updated)

### FlightAware API - FREE TIER PROTECTION

```yaml
FLIGHTAWARE_RATE_LIMIT_ENABLED=true
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_MINUTE=1    # Well below 5/min limit
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_HOUR=10     # Conservative hourly limit
FLIGHTAWARE_RATE_LIMIT_CALLS_PER_DAY=13      # 80% of monthly quota
```

**Monthly Projection**: 13/day × 30 = **390 requests/month** (78% of free tier)

**Cost**: $0 (stays within free tier)

---

### OpenAI API - $5 MONTHLY BUDGET PROTECTION

```yaml
OPENAI_RATE_LIMIT_ENABLED=true
OPENAI_RATE_LIMIT_CALLS_PER_MINUTE=3          # Prevent burst usage
OPENAI_RATE_LIMIT_CALLS_PER_HOUR=10           # Hourly cap
OPENAI_RATE_LIMIT_CALLS_PER_DAY=100           # 50% safety buffer
```

**Monthly Projection**: 100/day × 30 = **3,000 summaries/month**

**Cost**: 3,000 × $0.0008 = **$2.40/month** (48% of budget)

**If switching to GPT-4o Mini**: 3,000 × $0.00015 = **$0.45/month** (9% of budget!)

---

## 🚨 Hard Limits & Circuit Breakers

### Automatic Protection Mechanisms

#### 1. **Pre-Flight Rate Check**
```java
if (!rateLimiter.allowRequest()) {
    throw new RateLimitExceededException("Daily limit reached");
}
// API call is BLOCKED before being made
```

#### 2. **Redis-Backed Distributed Counters**
- Tracks usage across all service instances
- Atomic increment operations
- Automatic expiration (daily/hourly/minute)

#### 3. **HTTP 429 Response Handling**
- External API returns 429 → System backs off
- No retry if daily limit reached
- Logged for monitoring

#### 4. **Monthly Cost Alerts** (Manual Monitoring)
Check usage daily:
```bash
# FlightAware usage
docker-compose logs flightdata-service | findstr "usage"

# Count daily calls
docker-compose logs flightdata-service | findstr "Calling FlightAware" | Measure-Object | Select-Object Count
```

---

## 📊 Cost Protection Summary

| API | Free/Budget | Limit/Month | Daily Limit | Cost/Month | % Used |
|-----|-------------|-------------|-------------|------------|--------|
| **FlightAware** | FREE (500) | 390 requests | 13 | $0.00 | 78% |
| **OpenAI** | $5 | 3,000 summaries | 100 | $2.40 | 48% |
| **TOTAL** | **$5** | - | - | **$2.40** | **48%** |

### Safety Margins
- ✅ FlightAware: 22% buffer below free tier limit
- ✅ OpenAI: 52% budget remaining ($2.60 unused)
- ✅ Total monthly cost: **$2.40** (well under $5/API)

---

## 💡 Recommendations

### 1. Switch to GPT-4o Mini (HIGHLY RECOMMENDED!)
- **Same quality** for simple summaries
- **94% cost reduction** ($0.45 vs $2.40/month)
- Allows **33,333 summaries** per month with $5

**Change in `.env`:**
```env
OPENAI_MODEL=gpt-4o-mini
```

### 2. Monitor Usage Daily
```powershell
# Check FlightAware calls today
docker-compose logs --since 24h flightdata-service | findstr "FlightAware API call"

# Check OpenAI calls today
docker-compose logs --since 24h llm-summary-service | findstr "OpenAI API call"
```

### 3. Monthly Cost Review
Set calendar reminder to check:
- FlightAware dashboard: https://www.flightaware.com/commercial/aeroapi/portal.rvt
- OpenAI usage: https://platform.openai.com/account/usage

### 4. Emergency Shutdown
If you see unexpected high usage:
```powershell
# Stop services immediately
docker-compose stop flightdata-service llm-summary-service

# Check logs
docker-compose logs --tail=200 flightdata-service
docker-compose logs --tail=200 llm-summary-service
```

---

## 🔒 Security Measures Implemented

### 1. Environment Variables
- ✅ API keys never in code
- ✅ `.env` in `.gitignore`
- ✅ `.env.example` has placeholders only

### 2. Rate Limiting (Redis-Backed)
- ✅ Distributed counters (works with multiple instances)
- ✅ Atomic operations (no race conditions)
- ✅ Automatic expiration (daily/hourly/minute windows)

### 3. Pre-Flight Validation
- ✅ Checks limit BEFORE making API call
- ✅ No cost if limit reached
- ✅ Returns 429 to client immediately

### 4. Error Sanitization
- ✅ No API keys in logs
- ✅ No stack traces in production
- ✅ Generic error messages to clients

### 5. Monitoring & Alerts
- ✅ Usage logged at INFO level
- ✅ Warnings at 80% of limit
- ✅ Errors when limit exceeded

---

## 🎯 Bottom Line

### Your $5 + $5 = $10 Budget is SAFE!

| Protection Layer | Status |
|------------------|--------|
| FlightAware free tier (500/month) | ✅ Limited to 390/month (78%) |
| OpenAI $5 budget (6,250 summaries) | ✅ Limited to 3,000/month (48%) |
| Pre-flight rate checks | ✅ Blocks calls before cost incurred |
| Redis distributed tracking | ✅ Accurate across all instances |
| Daily/hourly/minute limits | ✅ Prevents burst overage |
| Manual monitoring tools | ✅ Commands provided |

**Actual Monthly Cost**: ~$2.40 (OpenAI) + $0 (FlightAware) = **$2.40 total**

**Budget Remaining**: $10 - $2.40 = **$7.60 unused** (76% safety margin!)

---

## 📞 Support

For cost concerns:
1. Check `SECURITY.md` for rate limiting details
2. Review logs daily: `docker-compose logs -f flightdata-service`
3. Monitor OpenAI dashboard: https://platform.openai.com/account/usage
4. Monitor FlightAware dashboard: https://www.flightaware.com/commercial/aeroapi/portal.rvt

**The system will automatically stop calling APIs when limits are reached!**

