# Start Infrastructure Script
# Starts all infrastructure services for local development

Write-Host "=== Starting Airline Tracker Infrastructure ===" -ForegroundColor Cyan
Write-Host ""

# Check if Docker is running
Write-Host "Checking Docker..." -ForegroundColor Yellow
try {
    docker info | Out-Null
    Write-Host "✓ Docker is running" -ForegroundColor Green
} catch {
    Write-Host "✗ Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

Write-Host ""

# Start services
Write-Host "Starting infrastructure services..." -ForegroundColor Yellow
Write-Host "  - PostgreSQL (Port 5432)" -ForegroundColor Gray
Write-Host "  - Redis (Port 6379)" -ForegroundColor Gray
Write-Host "  - Zookeeper (Port 2181)" -ForegroundColor Gray
Write-Host "  - Kafka (Port 9092)" -ForegroundColor Gray
Write-Host "  - Kafka UI (Port 8090)" -ForegroundColor Gray
Write-Host ""

docker compose up -d

Write-Host ""

# Wait for services to be healthy
Write-Host "Waiting for services to be healthy..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""

# Check service status
Write-Host "=== Service Status ===" -ForegroundColor Cyan
docker compose ps

Write-Host ""

# Display connection information
Write-Host "=== Connection Information ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "PostgreSQL:" -ForegroundColor Yellow
Write-Host "  Host: localhost" -ForegroundColor Gray
Write-Host "  Port: 5432" -ForegroundColor Gray
Write-Host "  Database: airline_tracker" -ForegroundColor Gray
Write-Host "  Username: airline_tracker_user" -ForegroundColor Gray
Write-Host "  Password: dev_password" -ForegroundColor Gray
Write-Host ""

Write-Host "Redis:" -ForegroundColor Yellow
Write-Host "  Host: localhost" -ForegroundColor Gray
Write-Host "  Port: 6379" -ForegroundColor Gray
Write-Host "  No password (dev environment)" -ForegroundColor Gray
Write-Host ""

Write-Host "Kafka:" -ForegroundColor Yellow
Write-Host "  Bootstrap Server: localhost:9092" -ForegroundColor Gray
Write-Host "  Topic: flight-data-events" -ForegroundColor Gray
Write-Host "  Kafka UI: http://localhost:8090" -ForegroundColor Gray
Write-Host ""

Write-Host "=== Infrastructure Ready ===" -ForegroundColor Green
Write-Host ""
Write-Host "To stop services, run:" -ForegroundColor Yellow
Write-Host "  docker compose down" -ForegroundColor Gray
Write-Host ""
Write-Host "To view logs, run:" -ForegroundColor Yellow
Write-Host "  docker compose logs -f [service-name]" -ForegroundColor Gray
Write-Host ""

