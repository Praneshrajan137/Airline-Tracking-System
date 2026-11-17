param(
    [string]$TestFlight = "UAL123",
    [string]$FrontendUrl = "http://localhost",
    [string]$GatewayUrl = "http://localhost:8080",
    [int]$TimeoutSeconds = 20
)

$ErrorActionPreference = "Stop"

function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    Write-Host "PASS  $Message" -ForegroundColor Green
}

function Get-SummaryOrNull([string]$Uri) {
    try {
        return Invoke-RestMethod -Uri $Uri -TimeoutSec 10
    } catch {
        $status = $null
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($status -eq 404) { return $null }
        throw
    }
}

$ident = $TestFlight.Trim().ToUpperInvariant()
$escapedIdent = [Uri]::EscapeDataString($ident)
$escapedFrontend = $FrontendUrl.TrimEnd('/')
$escapedGateway = $GatewayUrl.TrimEnd('/')

$page = Invoke-WebRequest -Uri "$escapedFrontend/" -TimeoutSec 10 -UseBasicParsing
Assert-Check ($page.StatusCode -eq 200) "Frontend responds"

$health = Invoke-RestMethod -Uri "$escapedGateway/actuator/health" -TimeoutSec 10
Assert-Check ($health.status -eq "UP") "Gateway health is UP"

$candidates = @(Invoke-RestMethod -Uri "$escapedGateway/api/v1/flight/$escapedIdent/candidates" -TimeoutSec 10)
Assert-Check ($candidates.Count -gt 0) "$ident returns at least one candidate"
$selected = $candidates[0]
Assert-Check ($selected.data_source -eq "Demo fixture") "Default demo profile is active; no provider call was needed"

$flightUrl = "$escapedGateway/api/v1/flight/$escapedIdent/instance/$([Uri]::EscapeDataString($selected.fa_flight_id))"
$flight = Invoke-RestMethod -Uri $flightUrl -TimeoutSec 10
Assert-Check ($flight.fa_flight_id -eq $selected.fa_flight_id) "Selected instance lookup preserves its ID"

$summaryUrl = "$escapedGateway/api/v1/flight/$escapedIdent/summary/$([Uri]::EscapeDataString($selected.fa_flight_id))"
$summary = Get-SummaryOrNull $summaryUrl
if (-not $summary) {
    $retryUrl = "$escapedGateway/api/v1/flight/$escapedIdent/instance/$([Uri]::EscapeDataString($selected.fa_flight_id))/summary/retry"
    $queued = Invoke-WebRequest -Uri $retryUrl -Method Post -TimeoutSec 10 -UseBasicParsing
    Assert-Check ($queued.StatusCode -eq 202) "Selected-instance summary request is queued"

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while (-not $summary -and [DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 1
        $summary = Get-SummaryOrNull $summaryUrl
    }
}

Assert-Check ($summary -and $summary.fa_flight_id -eq $selected.fa_flight_id) "Summary belongs to the selected instance"
Assert-Check ($summary.summary_text -like "Demo summary:*") "Summary is clearly demo fixture text"
Write-Host "Local demo smoke check completed." -ForegroundColor Cyan
