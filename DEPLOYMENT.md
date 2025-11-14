# Production Deployment Guide

## 🚀 Automated Deployment (Recommended)

Use the deployment scripts for automated setup with validation and health checks:

### Windows (PowerShell)
```powershell
.\scripts\deploy.ps1
```

### Linux/Mac/WSL (Bash)
```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh
```

The script will:
1. ✅ Check Docker/Docker Compose installation
2. ✅ Validate `.env` file and API keys
3. ✅ Build all images with `--no-cache`
4. ✅ Start services and wait for health checks (max 2 min)
5. ✅ Run smoke tests (Eureka, API Gateway, Flight API)
6. ✅ Display success message with URLs

See `scripts/README.md` for details.

---

## 📖 Manual Deployment

If you prefer manual deployment, follow these steps:

### 1. Prerequisites
- Docker Engine 20.10+ and Docker Compose 2.0+
- Minimum 8GB RAM, 4 CPU cores
- FlightAware API key ([Get one here](https://www.flightaware.com/commercial/aeroapi/))
- OpenAI API key ([Get one here](https://platform.openai.com/api-keys))

### 2. Setup Environment Variables

```bash
# Copy example file
cp env.example .env

# Edit with your API keys
nano .env  # or vim, code, etc.
```

**Required variables to change:**
- `FLIGHTAWARE_API_KEY` - Your FlightAware API key
- `OPENAI_API_KEY` - Your OpenAI API key
- `POSTGRES_PASSWORD` - Strong password (min 16 chars)

### 3. Build Services

```bash
# Build all Docker images
docker-compose build

# This will create:
# - airline-tracker/service-registry:latest
# - airline-tracker/api-gateway:latest
# - airline-tracker/flightdata-service:latest
# - airline-tracker/llm-summary-service:latest
```

### 4. Start Services

```bash
# Start all services in background
docker-compose up -d

# Check service health
docker-compose ps

# View logs
docker-compose logs -f
```

### 5. Verify Deployment

```bash
# Check API Gateway health
curl http://localhost:8080/actuator/health

# Check Eureka dashboard
open http://localhost:8761

# Test flight data endpoint
curl http://localhost:8080/api/v1/flight/UAL123
```

## Service URLs

| Service | URL | Description |
|---------|-----|-------------|
| API Gateway | http://localhost:8080 | Main entry point |
| Eureka Dashboard | http://localhost:8761 | Service registry |
| FlightData Service | http://localhost:8081 | Flight data API |
| LLM Summary Service | http://localhost:8082 | Summary API |
| PostgreSQL | localhost:5432 | Database |
| Redis | localhost:6379 | Cache |
| Kafka | localhost:9092 | Message broker |

## API Endpoints

### Get Flight Data
```bash
GET http://localhost:8080/api/v1/flight/{ident}

Example:
curl http://localhost:8080/api/v1/flight/UAL123
```

### Get Flight Summary
```bash
GET http://localhost:8080/api/v1/flight/{ident}/summary

Example:
curl http://localhost:8080/api/v1/flight/UAL123/summary
```

## Management Commands

### Start/Stop Services
```bash
# Start all services
docker-compose up -d

# Stop all services
docker-compose stop

# Stop and remove containers
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f flightdata-service

# Last 100 lines
docker-compose logs --tail=100 -f
```

### Restart Services
```bash
# Restart all
docker-compose restart

# Restart specific service
docker-compose restart flightdata-service
```

### Check Health
```bash
# Service status
docker-compose ps

# Resource usage
docker stats

# Container inspection
docker inspect prod-flightdata-service
```

## Data Persistence

Data is persisted in Docker volumes:

```bash
# List volumes
docker volume ls | grep airline

# Inspect volume
docker volume inspect airline-tracker-system_postgres_data

# Backup database
docker run --rm \
  -v airline-tracker-system_postgres_data:/data \
  -v $(pwd):/backup \
  ubuntu tar czf /backup/postgres_backup_$(date +%Y%m%d_%H%M%S).tar.gz /data

# Restore database
docker run --rm \
  -v airline-tracker-system_postgres_data:/data \
  -v $(pwd):/backup \
  ubuntu tar xzf /backup/postgres_backup_YYYYMMDD_HHMMSS.tar.gz -C /
```

## Scaling

### Scale Individual Services
```bash
# Scale flightdata-service to 3 instances
docker-compose up -d --scale flightdata-service=3

# Note: Remove port mappings for scaled services
```

### Horizontal Scaling
For production scaling, use:
- **Kubernetes** (recommended for cloud)
- **Docker Swarm** (simpler alternative)
- **ECS/EKS** (AWS)
- **Cloud Run** (GCP)

## Monitoring

### Health Checks
All services expose Spring Boot Actuator endpoints:

```bash
# API Gateway
curl http://localhost:8080/actuator/health

# FlightData Service
curl http://localhost:8081/actuator/health

# LLM Summary Service
curl http://localhost:8082/actuator/health
```

### Metrics
```bash
# Application metrics (JSON)
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Add Prometheus + Grafana (Optional)

See `env.example` for GRAFANA_PASSWORD configuration.

## Troubleshooting

### Services Won't Start
```bash
# Check Docker daemon
docker info

# Check resource availability
docker system df

# View detailed logs
docker-compose logs --tail=50 -f [service-name]
```

### Database Connection Issues
```bash
# Check PostgreSQL is running
docker-compose ps postgres

# Connect to database
docker exec -it prod-postgres psql -U airline_tracker_user -d airline_tracker

# List tables
\dt
```

### Redis Connection Issues
```bash
# Check Redis
docker exec -it prod-redis redis-cli ping

# Check cached keys
docker exec -it prod-redis redis-cli KEYS "flights::*"
```

### Kafka Issues
```bash
# Check Kafka brokers
docker exec -it prod-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# List topics
docker exec -it prod-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Consumer group status
docker exec -it prod-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group llm-summary-consumer-group
```

### Network Issues
```bash
# Inspect network
docker network inspect airline-tracker-system_airline-network

# Check DNS resolution
docker exec -it prod-api-gateway ping postgres
```

## Security Hardening

### 1. Use Strong Passwords
```bash
# Generate secure password
openssl rand -base64 32
```

### 2. Enable HTTPS
Use nginx or Traefik as reverse proxy:

```bash
# Example nginx config
server {
    listen 443 ssl;
    server_name api.yourdomain.com;
    
    ssl_certificate /etc/ssl/certs/cert.pem;
    ssl_certificate_key /etc/ssl/private/key.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 3. Restrict Network Access
```bash
# Expose only API Gateway to internet
# In docker-compose.yml, remove port mappings for internal services
```

### 4. Use Secrets Management
- AWS Secrets Manager
- HashiCorp Vault
- Docker Secrets (Swarm mode)

## Production Checklist

- [ ] Changed `POSTGRES_PASSWORD` to strong password
- [ ] Added real `FLIGHTAWARE_API_KEY`
- [ ] Added real `OPENAI_API_KEY`
- [ ] Configured `.env` file (not committed to git)
- [ ] Verified all services are healthy
- [ ] Tested API endpoints
- [ ] Set up backup strategy
- [ ] Configured monitoring/alerting
- [ ] Enabled HTTPS/TLS
- [ ] Configured firewall rules
- [ ] Documented deployment process
- [ ] Set up log aggregation
- [ ] Configured auto-scaling (if needed)

## Support

For issues or questions:
1. Check logs: `docker-compose logs -f`
2. Review Phase 5 test results: `integration-tests/PHASE5_TEST_RESULTS.md`
3. Check service health: `docker-compose ps`

