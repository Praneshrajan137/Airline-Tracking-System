# 📘 Operational Runbook
## Airline Tracking System - Operations Guide

**Version:** 1.0.0  
**Last Updated:** November 18, 2025  
**Audience:** DevOps Engineers, SREs, On-Call Engineers

---

## 📋 Table of Contents

1. [Deployment Checklist](#1-deployment-checklist)
2. [Monitoring & Alerts](#2-monitoring--alerts)
3. [Common Operational Tasks](#3-common-operational-tasks)
4. [Incident Response](#4-incident-response)
5. [Rollback Procedures](#5-rollback-procedures)
6. [Emergency Contacts](#6-emergency-contacts)

---

## 1. Deployment Checklist

### Pre-Deployment

#### Environment Preparation
- [ ] Docker & Docker Compose installed (20.10+, 2.0+)
- [ ] System resources: 8GB RAM, 4 CPU cores minimum
- [ ] API keys configured in `.env` file
- [ ] Network ports available (8761, 8080, 8081, 8082, 5432, 6379, 9092)

#### Configuration Validation
```bash
# Validate .env file exists
test -f .env && echo "✅ .env exists" || echo "❌ .env missing"

# Validate docker-compose
docker-compose config --quiet && echo "✅ Valid" || echo "❌ Invalid"
```

### Deployment Steps

```bash
# 1. Backup current state
docker exec prod-postgres pg_dump -U airline_tracker_user airline_tracker > backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Pull latest code
git pull origin main

# 3. Build services
docker-compose build --no-cache

# 4. Deploy
docker-compose down && docker-compose up -d

# 5. Verify health
curl -f http://localhost:8080/actuator/health
```

### Post-Deployment Verification

- [ ] All services show "healthy" in `docker-compose ps`
- [ ] Eureka dashboard shows 3 services registered
- [ ] API endpoints return 200 OK
- [ ] No ERROR level messages in logs
- [ ] Monitoring dashboards updated

---

## 2. Monitoring & Alerts

### Prometheus Metrics

**Access:** http://localhost:9090

**Key Metrics:**
```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) * 100

# Response time (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Cache hit rate
flight_cache_hits_total / (flight_cache_hits_total + flight_cache_misses_total) * 100
```

### Grafana Dashboards

**Access:** http://localhost:3000 (admin/admin)

**Pre-configured Dashboard:**
- Location: `monitoring/grafana/dashboards/airline-tracker-dashboard.json`
- Panels: Request rate, error rate, response times, cache metrics, JVM memory

### Alert Rules

**Critical Alerts (Page immediately):**

```yaml
# Service Down
- alert: ServiceDown
  expr: up{job=~".*-service"} == 0
  for: 1m
  severity: critical

# High Error Rate
- alert: HighErrorRate
  expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) > 0.05
  for: 5m
  severity: critical
```

**Warning Alerts:**

```yaml
# Slow Response Times
- alert: SlowResponseTime
  expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
  for: 10m
  severity: warning

# Low Cache Hit Rate
- alert: LowCacheHitRate
  expr: flight_cache_hits_total / (flight_cache_hits_total + flight_cache_misses_total) < 0.7
  for: 15m
  severity: warning
```

---

## 3. Common Operational Tasks

### 3.1 Restart Services

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart flightdata-service

# Restart with rebuild
docker-compose up -d --build flightdata-service

# Rolling restart (zero downtime)
docker-compose up -d --scale flightdata-service=2
sleep 30
docker-compose up -d --scale flightdata-service=1
```

### 3.2 View Logs

```bash
# All services, follow mode
docker-compose logs -f

# Specific service
docker-compose logs -f flightdata-service

# Last 50 lines
docker-compose logs --tail=50 flightdata-service

# Filter by log level
docker-compose logs -f | grep -E "ERROR|WARN"

# Export logs to file
docker-compose logs --no-color > logs_$(date +%Y%m%d_%H%M%S).txt
```

### 3.3 Database Backup

```bash
# Full database dump
docker exec prod-postgres pg_dump -U airline_tracker_user airline_tracker > backup_$(date +%Y%m%d_%H%M%S).sql

# Compressed backup
docker exec prod-postgres pg_dump -U airline_tracker_user airline_tracker | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz

# Verify backup
ls -lh backup_*.sql.gz
head -20 backup_*.sql
```

**Automated Backup Script:**
```bash
#!/bin/bash
# backup-db.sh
BACKUP_DIR="/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
docker exec prod-postgres pg_dump -U airline_tracker_user airline_tracker | gzip > "$BACKUP_DIR/airline_tracker_$TIMESTAMP.sql.gz"
find "$BACKUP_DIR" -name "airline_tracker_*.sql.gz" -mtime +30 -delete
```

**Schedule with cron:**
```bash
# Daily backup at 2 AM
0 2 * * * /path/to/backup-db.sh >> /var/log/backup.log 2>&1
```

**Restore from backup:**
```bash
# Stop application services
docker-compose stop api-gateway flightdata-service llm-summary-service

# Restore
gunzip -c backup_20251118_020000.sql.gz | docker exec -i prod-postgres psql -U airline_tracker_user airline_tracker

# Restart services
docker-compose start api-gateway flightdata-service llm-summary-service
```

### 3.4 Clear Redis Cache

```bash
# Flush all keys (WARNING: clears entire cache)
docker exec prod-redis redis-cli FLUSHALL

# List flight cache keys
docker exec prod-redis redis-cli KEYS "flights::*"

# Delete specific flight
docker exec prod-redis redis-cli DEL "flights::UAL123"

# Cache statistics
docker exec prod-redis redis-cli INFO stats
docker exec prod-redis redis-cli DBSIZE

# Check specific key TTL
docker exec prod-redis redis-cli TTL "flights::UAL123"
```

---

## 4. Incident Response

### Severity Levels

| Severity | Response Time | Examples |
|----------|---------------|----------|
| **P0 - Critical** | < 5 minutes | Complete system outage, database corruption |
| **P1 - High** | < 15 minutes | API Gateway down, high error rate (>10%) |
| **P2 - Medium** | < 1 hour | Slow response times, cache failures |
| **P3 - Low** | < 4 hours | Single service restart, low error rate |

### P0 - Critical: Complete System Outage

**Symptoms:** All API endpoints timeout, Eureka dashboard not accessible

**Actions:**
```bash
# 1. Check Docker daemon
docker info

# 2. Check all containers
docker-compose ps

# 3. Check system resources
df -h && free -h

# 4. Restart all services
docker-compose restart

# 5. If restart fails, full redeploy
docker-compose down && docker-compose up -d

# 6. Monitor logs
docker-compose logs -f | grep ERROR
```

### P0 - Critical: Database Corruption

**Actions:**
```bash
# 1. Stop all services
docker-compose stop

# 2. Backup current state
docker cp prod-postgres:/var/lib/postgresql/data ./postgres_corrupted_$(date +%Y%m%d_%H%M%S)

# 3. Try recovery
docker exec prod-postgres pg_resetwal -f /var/lib/postgresql/data

# 4. If recovery fails, restore from backup
docker-compose down -v
docker-compose up -d postgres
sleep 30
gunzip -c backup_latest.sql.gz | docker exec -i prod-postgres psql -U airline_tracker_user airline_tracker

# 5. Start application services
docker-compose up -d
```

### P1 - High: API Gateway Down

**Actions:**
```bash
# 1. Check Gateway logs
docker-compose logs --tail=100 api-gateway

# 2. Restart Gateway
docker-compose restart api-gateway

# 3. Verify Eureka registration
curl http://localhost:8761/eureka/apps/API-GATEWAY

# 4. Test endpoints
curl http://localhost:8080/actuator/health
```

### P1 - High: High Error Rate (>10%)

**Actions:**
```bash
# 1. Identify failing service
docker-compose logs -f | grep "500 Internal Server Error"

# 2. Check error patterns
docker-compose logs flightdata-service | grep ERROR | tail -50

# 3. Check external API status
curl https://aeroapi.flightaware.com/aeroapi/health

# 4. Check resource usage
docker stats

# 5. Restart affected service
docker-compose restart flightdata-service
```

### P2 - Medium: Slow Response Times

**Actions:**
```bash
# 1. Check cache hit rate
docker exec prod-redis redis-cli INFO stats | grep keyspace_hits

# 2. Check database connections
docker exec prod-postgres psql -U airline_tracker_user -d airline_tracker -c "SELECT count(*) FROM pg_stat_activity;"

# 3. Check for slow queries
docker-compose logs postgres | grep "duration.*ms" | tail -20

# 4. Check Kafka consumer lag
docker exec prod-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group llm-summary-consumer-group
```

### Incident Response Checklist

**During Incident:**
- [ ] Assess severity level
- [ ] Notify team (if P0/P1)
- [ ] Check monitoring dashboards
- [ ] Execute response actions
- [ ] Document actions taken
- [ ] Verify resolution

**Post-Incident:**
- [ ] Write incident report
- [ ] Conduct blameless postmortem
- [ ] Identify root cause
- [ ] Update runbook

---

## 5. Rollback Procedures

### Quick Rollback (Docker Images)

```bash
# 1. Stop current services
docker-compose down

# 2. Update docker-compose.yml to use previous tag
# Change: image: airline-tracker/flightdata-service:latest
# To:     image: airline-tracker/flightdata-service:v1.0.0

# 3. Start services with previous version
docker-compose up -d

# 4. Verify rollback
docker-compose ps
curl http://localhost:8080/actuator/health
```

### Database Rollback

```bash
# 1. Stop application services
docker-compose stop api-gateway flightdata-service llm-summary-service

# 2. Restore from pre-deployment backup
gunzip -c backup_before_deployment.sql.gz | docker exec -i prod-postgres psql -U airline_tracker_user airline_tracker

# 3. Restart application services
docker-compose start api-gateway flightdata-service llm-summary-service
```

### Configuration Rollback

```bash
# 1. Restore previous .env file
cp .env.backup .env

# 2. Restart affected services
docker-compose restart
```

### Code Rollback (Git)

```bash
# 1. Identify commit to rollback to
git log --oneline -10

# 2. Revert to previous commit
git revert HEAD

# 3. Rebuild and redeploy
docker-compose build && docker-compose up -d
```

### Rollback Verification

```bash
# 1. All services healthy
docker-compose ps

# 2. Health endpoints responding
curl http://localhost:8080/actuator/health

# 3. API endpoints working
curl http://localhost:8080/api/v1/flight/UAL123

# 4. No errors in logs
docker-compose logs --tail=100 | grep ERROR
```

---

## 6. Emergency Contacts

### On-Call Rotation

| Role | Primary | Backup | Phone |
|------|---------|--------|-------|
| **DevOps Lead** | [Name] | [Name] | [Phone] |
| **Backend Engineer** | [Name] | [Name] | [Phone] |
| **Database Admin** | [Name] | [Name] | [Phone] |

### Escalation Path

1. **Level 1:** On-call engineer (15 min response)
2. **Level 2:** Team lead (30 min response)
3. **Level 3:** Engineering manager (1 hour response)

### External Contacts

- **FlightAware Support:** support@flightaware.com
- **OpenAI Support:** https://help.openai.com
- **Cloud Provider:** [Contact info]

### Communication Channels

- **Slack:** #airline-tracker-alerts
- **PagerDuty:** airline-tracker-oncall
- **Status Page:** status.airline-tracker.com

---

## 📚 Additional Resources

- **Deployment Guide:** `DEPLOYMENT.md`
- **Architecture Documentation:** `docs/ARCHITECTURE.md`
- **API Specification:** `docs/API-SPEC.yml`
- **Phase 5 Test Results:** `integration-tests/PHASE5_TEST_RESULTS.md`
- **Project Completion Report:** `docs/PROJECT_COMPLETION_REPORT.md`

---

**Document Version:** 1.0.0  
**Last Updated:** November 18, 2025  
**Next Review:** December 18, 2025
