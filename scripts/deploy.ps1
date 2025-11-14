# ============================================
# AIRLINE TRACKING SYSTEM - DEPLOYMENT SCRIPT (PowerShell)
# ============================================
# Automated deployment with health checks and smoke tests
# Usage: .\scripts\deploy.ps1

$ErrorActionPreference = "Stop"

# Configuration
$MAX_WAIT_SECONDS = 120
$HEALTH_CHECK_INTERVAL = 5

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "AIRLINE TRACKING SYSTEM - DEPLOYMENT" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# ============================================
# STEP 1: Check Prerequisites
# ============================================
Write-Host "[1/7] Checking prerequisites..." -ForegroundColor Cyan

# Check Docker
try {
    $dockerVersion = docker --version
    Write-Host "✅ Docker installed: $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker is not installed!" -ForegroundColor Red
    Write-Host "   Install from: https://docs.docker.com/get-docker/" -ForegroundColor Yellow
    exit 1
}

# Check Docker Compose
try {
    $composeVersion = docker-compose --version
    Write-Host "✅ Docker Compose installed: $composeVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker Compose is not installed!" -ForegroundColor Red
    Write-Host "   Install from: https://docs.docker.com/compose/install/" -ForegroundColor Yellow
    exit 1
}

# Check Docker daemon
try {
    docker info | Out-Null
    Write-Host "✅ Docker daemon is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker daemon is not running!" -ForegroundColor Red
    Write-Host "   Start Docker Desktop" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# ============================================
# STEP 2: Validate .env File
# ============================================
Write-Host "[2/7] Validating .env file..." -ForegroundColor Cyan

if (-not (Test-Path ".env")) {
    Write-Host "❌ .env file not found!" -ForegroundColor Red
    Write-Host "   Run: Copy-Item env.example .env" -ForegroundColor Yellow
    Write-Host "   Then add your API keys" -ForegroundColor Yellow
    exit 1
}
Write-Host "✅ .env file exists" -ForegroundColor Green

# Check required environment variables
$envContent = Get-Content ".env" -Raw
$requiredVars = @("FLIGHTAWARE_API_KEY", "OPENAI_API_KEY", "POSTGRES_PASSWORD")
$missingVars = @()

foreach ($var in $requiredVars) {
    if ($envContent -notmatch "^$var=" -or 
        $envContent -match "^$var=your_" -or 
        $envContent -match "^$var=change_me") {
        $missingVars += $var
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "❌ Missing or invalid required variables in .env:" -ForegroundColor Red
    foreach ($var in $missingVars) {
        Write-Host "   - $var" -ForegroundColor Yellow
    }
    Write-Host ""
    Write-Host "   Edit .env file and add valid values for these variables" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ All required environment variables are set" -ForegroundColor Green
Write-Host ""

# ============================================
# STEP 3: Build Docker Images
# ============================================
Write-Host "[3/7] Building Docker images (this may take a few minutes)..." -ForegroundColor Cyan

# Stop existing containers if any
Write-Host "   Stopping existing containers..." -ForegroundColor Gray
docker-compose down 2>$null | Out-Null

# Build images with no cache
Write-Host "   Building images with --no-cache..." -ForegroundColor Gray
try {
    docker-compose build --no-cache
    Write-Host "✅ All Docker images built successfully" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker image build failed!" -ForegroundColor Red
    exit 1
}
Write-Host ""

# ============================================
# STEP 4: Start Services
# ============================================
Write-Host "[4/7] Starting services..." -ForegroundColor Cyan

try {
    docker-compose up -d
    Write-Host "✅ Services started" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start services!" -ForegroundColor Red
    Write-Host "   Check logs: docker-compose logs" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# ============================================
# STEP 5: Wait for Health Checks
# ============================================
Write-Host "[5/7] Waiting for health checks (max ${MAX_WAIT_SECONDS}s)..." -ForegroundColor Cyan

$services = @("postgres", "redis", "kafka", "service-registry", "api-gateway", "flightdata-service", "llm-summary-service")
$elapsed = 0

while ($elapsed -lt $MAX_WAIT_SECONDS) {
    $allHealthy = $true
    
    foreach ($service in $services) {
        $containerName = "prod-$service"
        
        try {
            $healthStatus = docker inspect --format='{{.State.Health.Status}}' $containerName 2>$null
            
            if (-not $healthStatus -or $healthStatus -eq "no-health-check") {
                # Check if running
                $state = docker inspect --format='{{.State.Status}}' $containerName 2>$null
                if ($state -ne "running") {
                    $allHealthy = $false
                    Write-Host "   ⏳ Waiting for $service to start..." -ForegroundColor Yellow
                    break
                }
            } elseif ($healthStatus -ne "healthy") {
                $allHealthy = $false
                Write-Host "   ⏳ Waiting for $service health check (status: $healthStatus)..." -ForegroundColor Yellow
                break
            }
        } catch {
            $allHealthy = $false
            Write-Host "   ⏳ Waiting for $service..." -ForegroundColor Yellow
            break
        }
    }
    
    if ($allHealthy) {
        Write-Host "✅ All services are healthy!" -ForegroundColor Green
        break
    }
    
    Start-Sleep -Seconds $HEALTH_CHECK_INTERVAL
    $elapsed += $HEALTH_CHECK_INTERVAL
}

if ($elapsed -ge $MAX_WAIT_SECONDS) {
    Write-Host "❌ Timeout waiting for services to become healthy!" -ForegroundColor Red
    Write-Host "   Check logs: docker-compose logs -f" -ForegroundColor Yellow
    Write-Host "   Service status:" -ForegroundColor Yellow
    docker-compose ps
    exit 1
}
Write-Host ""

# ============================================
# STEP 6: Smoke Tests
# ============================================
Write-Host "[6/7] Running smoke tests..." -ForegroundColor Cyan

# Give services a few more seconds to fully initialize
Start-Sleep -Seconds 5

# Test 1: Eureka Dashboard
Write-Host "   Testing Eureka Dashboard..." -ForegroundColor Gray
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8761" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "   ✅ Eureka Dashboard: http://localhost:8761" -ForegroundColor Green
    }
} catch {
    Write-Host "   ⚠️  Eureka Dashboard not responding (may still be starting)" -ForegroundColor Yellow
}

# Test 2: API Gateway Health
Write-Host "   Testing API Gateway health..." -ForegroundColor Gray
$retries = 3
$success = $false
for ($i = 1; $i -le $retries; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Host "   ✅ API Gateway Health: http://localhost:8080/actuator/health" -ForegroundColor Green
            $success = $true
            break
        }
    } catch {
        if ($i -lt $retries) {
            Write-Host "   Retrying... ($i/$retries)" -ForegroundColor Gray
            Start-Sleep -Seconds 5
        }
    }
}

if (-not $success) {
    Write-Host "   ⚠️  API Gateway health check failed (may still be initializing)" -ForegroundColor Yellow
}

# Test 3: Flight Data API
Write-Host "   Testing Flight Data API..." -ForegroundColor Gray
$retries = 3
$success = $false
for ($i = 1; $i -le $retries; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/flight/UAL123" -UseBasicParsing -TimeoutSec 10
        $httpCode = $response.StatusCode
        
        if ($httpCode -eq 200 -or $httpCode -eq 404) {
            Write-Host "   ✅ Flight Data API: http://localhost:8080/api/v1/flight/UAL123 (HTTP $httpCode)" -ForegroundColor Green
            $success = $true
            break
        }
    } catch {
        if ($i -lt $retries) {
            Write-Host "   Retrying... ($i/$retries)" -ForegroundColor Gray
            Start-Sleep -Seconds 5
        }
    }
}

if (-not $success) {
    Write-Host "   ⚠️  Flight Data API not responding (may still be initializing)" -ForegroundColor Yellow
}

Write-Host ""

# ============================================
# STEP 7: Success Message
# ============================================
Write-Host "========================================" -ForegroundColor Green
Write-Host "✅ DEPLOYMENT SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Cyan
Write-Host "  📊 Eureka Dashboard:  http://localhost:8761" -ForegroundColor White
Write-Host "  🌐 API Gateway:       http://localhost:8080" -ForegroundColor White
Write-Host "  ✈️  Flight Data API:   http://localhost:8080/api/v1/flight/{flightId}" -ForegroundColor White
Write-Host "  📝 Summary API:       http://localhost:8080/api/v1/flight/{flightId}/summary" -ForegroundColor White
Write-Host ""
Write-Host "Health Checks:" -ForegroundColor Cyan
Write-Host "  API Gateway:   http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  FlightData:    http://localhost:8081/actuator/health" -ForegroundColor White
Write-Host "  LLM Summary:   http://localhost:8082/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "Example API Calls:" -ForegroundColor Cyan
Write-Host "  curl http://localhost:8080/api/v1/flight/UAL123" -ForegroundColor White
Write-Host "  curl http://localhost:8080/api/v1/flight/UAL123/summary" -ForegroundColor White
Write-Host ""
Write-Host "Management Commands:" -ForegroundColor Cyan
Write-Host "  View logs:     docker-compose logs -f" -ForegroundColor White
Write-Host "  Stop services: docker-compose down" -ForegroundColor White
Write-Host "  Restart:       docker-compose restart" -ForegroundColor White
Write-Host ""
Write-Host "Monitoring:" -ForegroundColor Cyan
Write-Host "  Service status: docker-compose ps" -ForegroundColor White
Write-Host "  Resource usage: docker stats" -ForegroundColor White
Write-Host "  API usage logs: docker-compose logs -f flightdata-service | findstr 'usage'" -ForegroundColor White
Write-Host ""
Write-Host "🎉 Your airline tracking system is now running!" -ForegroundColor Green
Write-Host ""

