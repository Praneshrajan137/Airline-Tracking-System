# ============================================
# API USAGE MONITORING SCRIPT
# ============================================
# Monitors FlightAware and OpenAI API usage to protect $5 budgets
# Usage: .\scripts\monitor-usage.ps1

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "API USAGE MONITORING - $5 BUDGET PROTECTION" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# ============================================
# BUDGET LIMITS
# ============================================
$FLIGHTAWARE_DAILY_LIMIT = 13
$OPENAI_DAILY_LIMIT = 100

# ============================================
# Check if containers are running
# ============================================
$flightdataRunning = docker ps --filter "name=prod-flightdata-service" --filter "status=running" -q
$llmRunning = docker ps --filter "name=prod-llm-summary-service" --filter "status=running" -q

if (-not $flightdataRunning) {
    Write-Host "⚠️  FlightData service is not running" -ForegroundColor Yellow
    Write-Host "   Start with: docker-compose up -d" -ForegroundColor Gray
    Write-Host ""
}

if (-not $llmRunning) {
    Write-Host "⚠️  LLM Summary service is not running" -ForegroundColor Yellow
    Write-Host "   Start with: docker-compose up -d" -ForegroundColor Gray
    Write-Host ""
}

if (-not $flightdataRunning -and -not $llmRunning) {
    exit 1
}

Write-Host "📊 USAGE STATISTICS (Last 24 Hours)" -ForegroundColor Cyan
Write-Host ""

# ============================================
# FlightAware API Usage
# ============================================
if ($flightdataRunning) {
    Write-Host "🛫 FlightAware API (FREE TIER)" -ForegroundColor Yellow
    Write-Host "   Daily Limit: $FLIGHTAWARE_DAILY_LIMIT calls" -ForegroundColor Gray
    Write-Host ""
    
    $flightawareCalls = docker logs prod-flightdata-service --since 24h 2>&1 | Select-String "Calling FlightAware API" | Measure-Object | Select-Object -ExpandProperty Count
    
    if ($flightawareCalls -eq 0) {
        Write-Host "   ✅ No API calls today" -ForegroundColor Green
    } else {
        $percentUsed = [math]::Round(($flightawareCalls / $FLIGHTAWARE_DAILY_LIMIT) * 100, 1)
        
        if ($flightawareCalls -ge $FLIGHTAWARE_DAILY_LIMIT) {
            Write-Host "   🚨 LIMIT REACHED: $flightawareCalls/$FLIGHTAWARE_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Red
        } elseif ($percentUsed -ge 80) {
            Write-Host "   ⚠️  HIGH USAGE: $flightawareCalls/$FLIGHTAWARE_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Yellow
        } else {
            Write-Host "   ✅ Usage: $flightawareCalls/$FLIGHTAWARE_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Green
        }
    }
    
    Write-Host "   Monthly Projection: $($flightawareCalls * 30)/390 calls" -ForegroundColor Gray
    Write-Host "   Cost: $0.00 (free tier)" -ForegroundColor Green
    Write-Host ""
}

# ============================================
# OpenAI API Usage
# ============================================
if ($llmRunning) {
    Write-Host "🤖 OpenAI API ($5 BUDGET)" -ForegroundColor Yellow
    Write-Host "   Daily Limit: $OPENAI_DAILY_LIMIT calls" -ForegroundColor Gray
    Write-Host ""
    
    $openaiCalls = docker logs prod-llm-summary-service --since 24h 2>&1 | Select-String "Calling OpenAI API" | Measure-Object | Select-Object -ExpandProperty Count
    
    if ($openaiCalls -eq 0) {
        Write-Host "   ✅ No API calls today" -ForegroundColor Green
        $monthlyCost = 0
    } else {
        $percentUsed = [math]::Round(($openaiCalls / $OPENAI_DAILY_LIMIT) * 100, 1)
        $monthlyCost = [math]::Round($openaiCalls * 30 * 0.00015, 2)
        
        if ($openaiCalls -ge $OPENAI_DAILY_LIMIT) {
            Write-Host "   🚨 LIMIT REACHED: $openaiCalls/$OPENAI_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Red
        } elseif ($percentUsed -ge 80) {
            Write-Host "   ⚠️  HIGH USAGE: $openaiCalls/$OPENAI_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Yellow
        } else {
            Write-Host "   ✅ Usage: $openaiCalls/$OPENAI_DAILY_LIMIT calls ($percentUsed%)" -ForegroundColor Green
        }
    }
    
    Write-Host "   Monthly Projection: $($openaiCalls * 30)/3,000 calls" -ForegroundColor Gray
    Write-Host "   Estimated Cost: `$$monthlyCost/month (gpt-4o-mini)" -ForegroundColor Green
    Write-Host ""
}

# ============================================
# Rate Limit Events
# ============================================
Write-Host "🚨 RATE LIMIT EVENTS (Last 24 Hours)" -ForegroundColor Cyan
Write-Host ""

if ($flightdataRunning) {
    $flightawareBlocked = docker logs prod-flightdata-service --since 24h 2>&1 | Select-String "Rate limit exceeded" | Measure-Object | Select-Object -ExpandProperty Count
    
    if ($flightawareBlocked -gt 0) {
        Write-Host "   ⚠️  FlightAware: $flightawareBlocked requests blocked" -ForegroundColor Yellow
    } else {
        Write-Host "   ✅ FlightAware: No requests blocked" -ForegroundColor Green
    }
}

if ($llmRunning) {
    $openaiBlocked = docker logs prod-llm-summary-service --since 24h 2>&1 | Select-String "Rate limit exceeded" | Measure-Object | Select-Object -ExpandProperty Count
    
    if ($openaiBlocked -gt 0) {
        Write-Host "   ⚠️  OpenAI: $openaiBlocked requests blocked" -ForegroundColor Yellow
    } else {
        Write-Host "   ✅ OpenAI: No requests blocked" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "💰 TOTAL ESTIMATED MONTHLY COST" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

if ($openaiCalls -gt 0) {
    $totalMonthlyCost = [math]::Round($openaiCalls * 30 * 0.00015, 2)
    $percentOfBudget = [math]::Round(($totalMonthlyCost / 5) * 100, 1)
    
    Write-Host "   FlightAware: `$0.00 (free tier)" -ForegroundColor Green
    Write-Host "   OpenAI: `$$totalMonthlyCost" -ForegroundColor Green
    Write-Host "   ----------------------------------------" -ForegroundColor Gray
    Write-Host "   TOTAL: `$$totalMonthlyCost/month ($percentOfBudget% of `$5 budget)" -ForegroundColor Green
    Write-Host "   Remaining: `$$([math]::Round(5 - $totalMonthlyCost, 2))" -ForegroundColor Green
} else {
    Write-Host "   FlightAware: `$0.00 (free tier)" -ForegroundColor Green
    Write-Host "   OpenAI: `$0.00 (no calls yet)" -ForegroundColor Green
    Write-Host "   ----------------------------------------" -ForegroundColor Gray
    Write-Host "   TOTAL: `$0.00/month" -ForegroundColor Green
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "💡 Tips:" -ForegroundColor Yellow
Write-Host "   • Run this script daily to monitor usage" -ForegroundColor Gray
Write-Host "   • Check official dashboards monthly:" -ForegroundColor Gray
Write-Host "     - OpenAI: https://platform.openai.com/account/usage" -ForegroundColor Gray
Write-Host "     - FlightAware: https://www.flightaware.com/commercial/aeroapi/portal.rvt" -ForegroundColor Gray
Write-Host "   • If usage is high, review application logs" -ForegroundColor Gray
Write-Host ""

