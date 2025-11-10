# Stop Infrastructure Script
# Stops all infrastructure services

Write-Host "=== Stopping Airline Tracker Infrastructure ===" -ForegroundColor Cyan
Write-Host ""

docker compose down

Write-Host ""
Write-Host "✓ Infrastructure services stopped" -ForegroundColor Green
Write-Host ""
Write-Host "To remove volumes (data will be lost), run:" -ForegroundColor Yellow
Write-Host "  docker compose down -v" -ForegroundColor Gray
Write-Host ""

