# ============================================
# PRODUCTION VALIDATION SCRIPT
# ============================================
# Tests the complete Airline Tracking System end-to-end
#
# Requirements:
# - Docker Compose services running (docker-compose up -d)
# - PowerShell 5.1+ (Windows) or PowerShell Core (cross-platform)
#
# Usage:
#   .\scripts\validate-production.ps1
#
# Exit Codes:
#   0 = All tests passed
#   1 = One or more tests failed

param(
    [string]$TestFlight = "UAL123",
    [int]$TimeoutSeconds = 60
)

# Colors for output
$SuccessColor = "Green"
$ErrorColor = "Red"
$InfoColor = "Cyan"
$WarningColor = "Yellow"

# Test counters
$script:TotalTests = 0
$script:PassedTests = 0
$script:FailedTests = 0
$script:TestResults = @()

# ============================================
# HELPER FUNCTIONS
# ============================================

function Write-TestHeader {
    param([string]$Message)
    Write-Host "`n========================================" -ForegroundColor $InfoColor
    Write-Host "  $Message" -ForegroundColor $InfoColor
    Write-Host "========================================`n" -ForegroundColor $InfoColor
}

function Write-TestStep {
    param([string]$Message)
    Write-Host "[TEST] $Message" -ForegroundColor $InfoColor
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor $SuccessColor
}

function Write-Failure {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor $ErrorColor
}

function Write-Warning {
    param([string]$Message)
    Write-Host "⚠️  $Message" -ForegroundColor $WarningColor
}

function Test-Assertion {
    param(
        [string]$TestName,
        [scriptblock]$Condition,
        [string]$SuccessMessage,
        [string]$FailureMessage
    )

    $script:TotalTests++

    try {
        $result = & $Condition
        if ($result) {
            $script:PassedTests++
            Write-Success "$TestName - $SuccessMessage"
            $script:TestResults += [PSCustomObject]@{
                Test = $TestName
                Status = "PASS"
                Message = $SuccessMessage
            }
            return $true
        } else {
            $script:FailedTests++
            Write-Failure "$TestName - $FailureMessage"
            $script:TestResults += [PSCustomObject]@{
                Test = $TestName
                Status = "FAIL"
                Message = $FailureMessage
            }
            return $false
        }
    } catch {
        $script:FailedTests++
        Write-Failure "$TestName - ERROR: $($_.Exception.Message)"
        $script:TestResults += [PSCustomObject]@{
            Test = $TestName
            Status = "FAIL"
            Message = "ERROR: $($_.Exception.Message)"
        }
        return $false
    }
}

function Invoke-RestApiCall {
    param(
        [string]$Url,
        [int]$ExpectedStatusCode = 200,
        [int]$TimeoutSec = 10
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -TimeoutSec $TimeoutSec -UseBasicParsing
        return @{
            Success = ($response.StatusCode -eq $ExpectedStatusCode)
            StatusCode = $response.StatusCode
            Content = $response.Content
        }
    } catch {
        return @{
            Success = $false
            StatusCode = $_.Exception.Response.StatusCode.value__
            Content = $null
            Error = $_.Exception.Message
        }
    }
}

# ============================================
# TEST SUITE
# ============================================

Write-TestHeader "AIRLINE TRACKING SYSTEM - PRODUCTION VALIDATION"
Write-Host "Test Flight: $TestFlight" -ForegroundColor $InfoColor
Write-Host "Timeout: $TimeoutSeconds seconds" -ForegroundColor $InfoColor
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor $InfoColor
Write-Host ""

# ============================================
# PHASE 1: INFRASTRUCTURE HEALTH CHECKS
# ============================================

Write-TestHeader "PHASE 1: Infrastructure Health Checks"

# Test 1.1: Service Registry (Eureka)
Test-Assertion -TestName "Eureka Server" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:8761/actuator/health"
    $result.Success
} -SuccessMessage "Eureka Server is UP (Port 8761)" `
  -FailureMessage "Eureka Server is DOWN or unreachable"

# Test 1.2: API Gateway
Test-Assertion -TestName "API Gateway" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:8080/actuator/health"
    $result.Success
} -SuccessMessage "API Gateway is UP (Port 8080)" `
  -FailureMessage "API Gateway is DOWN or unreachable"

# Test 1.3: FlightData Service
Test-Assertion -TestName "FlightData Service" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:8081/actuator/health"
    $result.Success
} -SuccessMessage "FlightData Service is UP (Port 8081)" `
  -FailureMessage "FlightData Service is DOWN or unreachable"

# Test 1.4: LLM Summary Service
Test-Assertion -TestName "LLM Summary Service" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:8082/actuator/health"
    $result.Success
} -SuccessMessage "LLM Summary Service is UP (Port 8082)" `
  -FailureMessage "LLM Summary Service is DOWN or unreachable"

# Test 1.5: Prometheus Monitoring
Test-Assertion -TestName "Prometheus" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:9090/-/healthy"
    $result.Success
} -SuccessMessage "Prometheus is UP (Port 9090)" `
  -FailureMessage "Prometheus is DOWN or unreachable"

# ============================================
# PHASE 2: SERVICE DISCOVERY
# ============================================

Write-TestHeader "PHASE 2: Service Discovery Verification"

# Test 2.1: All services registered with Eureka
Test-Assertion -TestName "Service Registration" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:8080/actuator/health"
    if ($result.Success) {
        $health = $result.Content | ConvertFrom-Json
        $services = $health.components.discoveryComposite.components.eureka.details.applications
        ($services.'FLIGHTDATA-SERVICE' -ge 1) -and
        ($services.'LLM-SUMMARY-SERVICE' -ge 1) -and
        ($services.'API-GATEWAY' -ge 1)
    } else {
        $false
    }
} -SuccessMessage "All 3 services registered with Eureka" `
  -FailureMessage "Services not properly registered with Eureka"

# ============================================
# PHASE 3: END-TO-END FLOW TEST
# ============================================

Write-TestHeader "PHASE 3: End-to-End Flow Test"

Write-Host "Testing complete flow:" -ForegroundColor $InfoColor
Write-Host "  1. API Gateway → FlightData Service" -ForegroundColor $InfoColor
Write-Host "  2. FlightAware API call (or cache)" -ForegroundColor $InfoColor
Write-Host "  3. Redis caching" -ForegroundColor $InfoColor
Write-Host "  4. Kafka event publishing" -ForegroundColor $InfoColor
Write-Host "  5. LLM Summary Service (async)" -ForegroundColor $InfoColor
Write-Host "  6. OpenAI summary generation" -ForegroundColor $InfoColor
Write-Host "  7. PostgreSQL persistence" -ForegroundColor $InfoColor
Write-Host ""

# Test 3.1: Get Flight Data (via API Gateway)
Write-TestStep "Step 1: Requesting flight data for $TestFlight..."
$flightDataUrl = "http://localhost:8080/api/v1/flight/$TestFlight"
$flightDataResult = Invoke-RestApiCall -Url $flightDataUrl -TimeoutSec 15

Test-Assertion -TestName "Flight Data Retrieval" -Condition {
    $flightDataResult.Success
} -SuccessMessage "Successfully retrieved flight data for $TestFlight" `
  -FailureMessage "Failed to retrieve flight data (Status: $($flightDataResult.StatusCode))"

# Test 3.2: Verify Flight Data Structure
if ($flightDataResult.Success) {
    $flightData = $flightDataResult.Content | ConvertFrom-Json

    Test-Assertion -TestName "Flight Data - ident field" -Condition {
        $flightData.ident -eq $TestFlight
    } -SuccessMessage "Flight ident matches: $($flightData.ident)" `
      -FailureMessage "Flight ident mismatch or missing"

    Test-Assertion -TestName "Flight Data - fa_flight_id field" -Condition {
        $null -ne $flightData.fa_flight_id -and $flightData.fa_flight_id.Length -gt 0
    } -SuccessMessage "fa_flight_id present: $($flightData.fa_flight_id)" `
      -FailureMessage "fa_flight_id missing or empty"

    Test-Assertion -TestName "Flight Data - status field" -Condition {
        $null -ne $flightData.status -and $flightData.status.Length -gt 0
    } -SuccessMessage "Status present: $($flightData.status)" `
      -FailureMessage "Status missing or empty"
}

# Test 3.3: Wait for Async Processing (Kafka → LLM Service)
Write-TestStep "Step 2: Waiting for Kafka event processing (max $TimeoutSeconds seconds)..."
Write-Host "  (FlightData Service publishes → Kafka → LLM Summary Service consumes)" -ForegroundColor Gray

Start-Sleep -Seconds 5  # Initial wait for Kafka processing

# Test 3.4: Retrieve LLM Summary (with retry logic)
Write-TestStep "Step 3: Requesting AI-generated summary..."

$summaryRetrieved = $false
$maxAttempts = 12  # 12 attempts * 5 seconds = 60 seconds
$attempt = 0

$summaryUrl = "http://localhost:8080/api/v1/flight/$TestFlight/summary"

while (-not $summaryRetrieved -and $attempt -lt $maxAttempts) {
    $attempt++
    Write-Host "  Attempt $attempt/$maxAttempts..." -ForegroundColor Gray

    $summaryResult = Invoke-RestApiCall -Url $summaryUrl -TimeoutSec 10

    if ($summaryResult.Success) {
        $summaryRetrieved = $true
        $summaryData = $summaryResult.Content | ConvertFrom-Json

        Test-Assertion -TestName "LLM Summary Retrieval" -Condition {
            $true
        } -SuccessMessage "Successfully retrieved AI summary after $attempt attempts" `
          -FailureMessage "N/A"

        Test-Assertion -TestName "Summary - ident field" -Condition {
            $summaryData.ident -eq $TestFlight
        } -SuccessMessage "Summary ident matches: $($summaryData.ident)" `
          -FailureMessage "Summary ident mismatch"

        Test-Assertion -TestName "Summary - summary_text field" -Condition {
            $null -ne $summaryData.summary_text -and $summaryData.summary_text.Length -gt 20
        } -SuccessMessage "Summary text present ($(($summaryData.summary_text).Length) chars)" `
          -FailureMessage "Summary text missing or too short"

        Test-Assertion -TestName "Summary - generated_at field" -Condition {
            $null -ne $summaryData.generated_at
        } -SuccessMessage "Timestamp present: $($summaryData.generated_at)" `
          -FailureMessage "Timestamp missing"

        Write-Host ""
        Write-Host "📝 Generated Summary:" -ForegroundColor $InfoColor
        Write-Host "  $($summaryData.summary_text)" -ForegroundColor White
        Write-Host ""

        break
    } else {
        if ($attempt -lt $maxAttempts) {
            Start-Sleep -Seconds 5
        }
    }
}

if (-not $summaryRetrieved) {
    Test-Assertion -TestName "LLM Summary Retrieval" -Condition {
        $false
    } -SuccessMessage "N/A" `
      -FailureMessage "Summary not generated within $TimeoutSeconds seconds (async processing issue)"
}

# ============================================
# PHASE 4: CACHING VERIFICATION
# ============================================

Write-TestHeader "PHASE 4: Redis Caching Verification"

Write-TestStep "Testing cache hit (second request should be faster)..."

# Measure first request (should hit cache now)
$stopwatch1 = [System.Diagnostics.Stopwatch]::StartNew()
$cachedResult1 = Invoke-RestApiCall -Url $flightDataUrl -TimeoutSec 10
$stopwatch1.Stop()
$duration1 = $stopwatch1.ElapsedMilliseconds

# Measure second request (definitely from cache)
Start-Sleep -Milliseconds 100
$stopwatch2 = [System.Diagnostics.Stopwatch]::StartNew()
$cachedResult2 = Invoke-RestApiCall -Url $flightDataUrl -TimeoutSec 10
$stopwatch2.Stop()
$duration2 = $stopwatch2.ElapsedMilliseconds

Test-Assertion -TestName "Cache Performance" -Condition {
    $cachedResult1.Success -and $cachedResult2.Success -and $duration2 -lt 500
} -SuccessMessage "Cache hit confirmed (Request: ${duration1}ms, Cached: ${duration2}ms)" `
  -FailureMessage "Caching not working as expected"

# ============================================
# PHASE 5: MONITORING & METRICS
# ============================================

Write-TestHeader 'PHASE 5: Monitoring & Metrics'

# Test 5.1: Prometheus Targets
Test-Assertion -TestName "Prometheus Targets" -Condition {
    $result = Invoke-RestApiCall -Url "http://localhost:9090/api/v1/targets"
    if ($result.Success) {
        $targets = ($result.Content | ConvertFrom-Json).data.activeTargets
        $upTargets = $targets | Where-Object { $_.health -eq "up" }
        $upTargets.Count -ge 4  # At least 4 services
    } else {
        $false
    }
} -SuccessMessage "Prometheus monitoring active (multiple targets UP)" `
  -FailureMessage "Prometheus targets not healthy"

# ============================================
# FINAL REPORT
# ============================================

Write-TestHeader "VALIDATION REPORT"

Write-Host "Total Tests: $script:TotalTests" -ForegroundColor $InfoColor
Write-Host "Passed:      $script:PassedTests ✅" -ForegroundColor $SuccessColor
Write-Host "Failed:      $script:FailedTests ❌" -ForegroundColor $(if ($script:FailedTests -eq 0) { $SuccessColor } else { $ErrorColor })
Write-Host "Success Rate: $([math]::Round(($script:PassedTests / $script:TotalTests) * 100, 2))%" -ForegroundColor $InfoColor
Write-Host ""

if ($script:FailedTests -eq 0) {
    Write-Host "========================================" -ForegroundColor $SuccessColor
    Write-Host "  ✅ ALL TESTS PASSED!" -ForegroundColor $SuccessColor
    Write-Host "  System is fully operational." -ForegroundColor $SuccessColor
    Write-Host "========================================" -ForegroundColor $SuccessColor
    Write-Host ""
    exit 0
} else {
    Write-Host "========================================" -ForegroundColor $ErrorColor
    Write-Host "  ❌ SOME TESTS FAILED" -ForegroundColor $ErrorColor
    Write-Host "  Review errors above for details." -ForegroundColor $ErrorColor
    Write-Host "========================================" -ForegroundColor $ErrorColor
    Write-Host ""
    Write-Host "Failed Tests:" -ForegroundColor $ErrorColor
    foreach ($result in $script:TestResults) {
        if ($result.Status -eq "FAIL") {
            $testName = $result.Test
            $message = $result.Message
            Write-Host "  - ${testName}: ${message}" -ForegroundColor $ErrorColor
        }
    }
    Write-Host ""

    exit 1
}
