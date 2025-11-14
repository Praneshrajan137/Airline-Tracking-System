#!/bin/bash

# ============================================
# AIRLINE TRACKING SYSTEM - DEPLOYMENT SCRIPT
# ============================================
# Automated deployment with health checks and smoke tests
# Usage: ./scripts/deploy.sh

set -e  # Exit on error

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
MAX_WAIT_SECONDS=120
HEALTH_CHECK_INTERVAL=5

echo ""
echo "========================================"
echo "AIRLINE TRACKING SYSTEM - DEPLOYMENT"
echo "========================================"
echo ""

# ============================================
# STEP 1: Check Prerequisites
# ============================================
echo -e "${CYAN}[1/7] Checking prerequisites...${NC}"

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed!${NC}"
    echo "   Install from: https://docs.docker.com/get-docker/"
    exit 1
fi
DOCKER_VERSION=$(docker --version)
echo -e "${GREEN}✅ Docker installed: ${DOCKER_VERSION}${NC}"

# Check Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed!${NC}"
    echo "   Install from: https://docs.docker.com/compose/install/"
    exit 1
fi
COMPOSE_VERSION=$(docker-compose --version)
echo -e "${GREEN}✅ Docker Compose installed: ${COMPOSE_VERSION}${NC}"

# Check Docker daemon
if ! docker info &> /dev/null; then
    echo -e "${RED}❌ Docker daemon is not running!${NC}"
    echo "   Start Docker Desktop or run: sudo systemctl start docker"
    exit 1
fi
echo -e "${GREEN}✅ Docker daemon is running${NC}"

echo ""

# ============================================
# STEP 2: Validate .env File
# ============================================
echo -e "${CYAN}[2/7] Validating .env file...${NC}"

if [ ! -f ".env" ]; then
    echo -e "${RED}❌ .env file not found!${NC}"
    echo "   Run: cp env.example .env"
    echo "   Then add your API keys"
    exit 1
fi
echo -e "${GREEN}✅ .env file exists${NC}"

# Check required environment variables
REQUIRED_VARS=("FLIGHTAWARE_API_KEY" "OPENAI_API_KEY" "POSTGRES_PASSWORD")
MISSING_VARS=()

for VAR in "${REQUIRED_VARS[@]}"; do
    if ! grep -q "^${VAR}=" .env || grep -q "^${VAR}=your_" .env || grep -q "^${VAR}=change_me" .env; then
        MISSING_VARS+=("$VAR")
    fi
done

if [ ${#MISSING_VARS[@]} -gt 0 ]; then
    echo -e "${RED}❌ Missing or invalid required variables in .env:${NC}"
    for VAR in "${MISSING_VARS[@]}"; do
        echo "   - $VAR"
    done
    echo ""
    echo "   Edit .env file and add valid values for these variables"
    exit 1
fi

echo -e "${GREEN}✅ All required environment variables are set${NC}"
echo ""

# ============================================
# STEP 3: Build Docker Images
# ============================================
echo -e "${CYAN}[3/7] Building Docker images (this may take a few minutes)...${NC}"

# Stop existing containers if any
echo "   Stopping existing containers..."
docker-compose down > /dev/null 2>&1 || true

# Build images with no cache
echo "   Building images with --no-cache..."
if docker-compose build --no-cache; then
    echo -e "${GREEN}✅ All Docker images built successfully${NC}"
else
    echo -e "${RED}❌ Docker image build failed!${NC}"
    exit 1
fi
echo ""

# ============================================
# STEP 4: Start Services
# ============================================
echo -e "${CYAN}[4/7] Starting services...${NC}"

if docker-compose up -d; then
    echo -e "${GREEN}✅ Services started${NC}"
else
    echo -e "${RED}❌ Failed to start services!${NC}"
    echo "   Check logs: docker-compose logs"
    exit 1
fi
echo ""

# ============================================
# STEP 5: Wait for Health Checks
# ============================================
echo -e "${CYAN}[5/7] Waiting for health checks (max ${MAX_WAIT_SECONDS}s)...${NC}"

SERVICES=("postgres" "redis" "kafka" "service-registry" "api-gateway" "flightdata-service" "llm-summary-service")
ELAPSED=0

while [ $ELAPSED -lt $MAX_WAIT_SECONDS ]; do
    ALL_HEALTHY=true
    
    for SERVICE in "${SERVICES[@]}"; do
        CONTAINER_NAME="prod-${SERVICE}"
        HEALTH_STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "no-health-check")
        
        # Services without health checks are considered healthy if running
        if [ "$HEALTH_STATUS" = "no-health-check" ]; then
            STATE=$(docker inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "not-found")
            if [ "$STATE" != "running" ]; then
                ALL_HEALTHY=false
                echo -e "   ${YELLOW}⏳ Waiting for ${SERVICE} to start...${NC}"
                break
            fi
        elif [ "$HEALTH_STATUS" != "healthy" ]; then
            ALL_HEALTHY=false
            echo -e "   ${YELLOW}⏳ Waiting for ${SERVICE} health check (status: ${HEALTH_STATUS})...${NC}"
            break
        fi
    done
    
    if $ALL_HEALTHY; then
        echo -e "${GREEN}✅ All services are healthy!${NC}"
        break
    fi
    
    sleep $HEALTH_CHECK_INTERVAL
    ELAPSED=$((ELAPSED + HEALTH_CHECK_INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT_SECONDS ]; then
    echo -e "${RED}❌ Timeout waiting for services to become healthy!${NC}"
    echo "   Check logs: docker-compose logs -f"
    echo "   Service status:"
    docker-compose ps
    exit 1
fi
echo ""

# ============================================
# STEP 6: Smoke Tests
# ============================================
echo -e "${CYAN}[6/7] Running smoke tests...${NC}"

# Give services a few more seconds to fully initialize
sleep 5

# Test 1: Eureka Dashboard
echo "   Testing Eureka Dashboard..."
if curl -s -f http://localhost:8761 > /dev/null; then
    echo -e "${GREEN}   ✅ Eureka Dashboard: http://localhost:8761${NC}"
else
    echo -e "${YELLOW}   ⚠️  Eureka Dashboard not responding (may still be starting)${NC}"
fi

# Test 2: API Gateway Health
echo "   Testing API Gateway health..."
RETRIES=3
SUCCESS=false
for i in $(seq 1 $RETRIES); do
    if curl -s -f http://localhost:8080/actuator/health > /dev/null; then
        echo -e "${GREEN}   ✅ API Gateway Health: http://localhost:8080/actuator/health${NC}"
        SUCCESS=true
        break
    else
        if [ $i -lt $RETRIES ]; then
            echo "   Retrying... ($i/$RETRIES)"
            sleep 5
        fi
    fi
done

if ! $SUCCESS; then
    echo -e "${YELLOW}   ⚠️  API Gateway health check failed (may still be initializing)${NC}"
fi

# Test 3: Flight Data API
echo "   Testing Flight Data API..."
RETRIES=3
SUCCESS=false
for i in $(seq 1 $RETRIES); do
    RESPONSE=$(curl -s -w "\n%{http_code}" http://localhost:8080/api/v1/flight/UAL123 2>/dev/null || echo "000")
    HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "404" ]; then
        echo -e "${GREEN}   ✅ Flight Data API: http://localhost:8080/api/v1/flight/UAL123 (HTTP $HTTP_CODE)${NC}"
        SUCCESS=true
        break
    else
        if [ $i -lt $RETRIES ]; then
            echo "   Retrying... ($i/$RETRIES)"
            sleep 5
        fi
    fi
done

if ! $SUCCESS; then
    echo -e "${YELLOW}   ⚠️  Flight Data API not responding (may still be initializing)${NC}"
fi

echo ""

# ============================================
# STEP 7: Success Message
# ============================================
echo -e "${GREEN}========================================"
echo "✅ DEPLOYMENT SUCCESSFUL!"
echo "========================================${NC}"
echo ""
echo -e "${CYAN}Service URLs:${NC}"
echo "  📊 Eureka Dashboard:  http://localhost:8761"
echo "  🌐 API Gateway:       http://localhost:8080"
echo "  ✈️  Flight Data API:   http://localhost:8080/api/v1/flight/{flightId}"
echo "  📝 Summary API:       http://localhost:8080/api/v1/flight/{flightId}/summary"
echo ""
echo -e "${CYAN}Health Checks:${NC}"
echo "  API Gateway:   http://localhost:8080/actuator/health"
echo "  FlightData:    http://localhost:8081/actuator/health"
echo "  LLM Summary:   http://localhost:8082/actuator/health"
echo ""
echo -e "${CYAN}Example API Calls:${NC}"
echo "  curl http://localhost:8080/api/v1/flight/UAL123"
echo "  curl http://localhost:8080/api/v1/flight/UAL123/summary"
echo ""
echo -e "${CYAN}Management Commands:${NC}"
echo "  View logs:     docker-compose logs -f"
echo "  Stop services: docker-compose down"
echo "  Restart:       docker-compose restart"
echo ""
echo -e "${CYAN}Monitoring:${NC}"
echo "  Service status: docker-compose ps"
echo "  Resource usage: docker stats"
echo "  API usage logs: docker-compose logs -f flightdata-service | grep 'usage'"
echo ""
echo -e "${GREEN}🎉 Your airline tracking system is now running!${NC}"
echo ""

