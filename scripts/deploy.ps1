$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Compose is required." }
docker info | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop must be running." }

$previousProfile = $env:APP_PROFILE
try {
    # Force this helper to use local fixtures even if .env selects another profile.
    $env:APP_PROFILE = "demo"
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Compose configuration is invalid." }
    docker compose up --build -d
    if ($LASTEXITCODE -ne 0) { throw "The local demo failed to start." }
    docker compose ps
    Write-Host "Local demo containers launched. Wait for health checks, then open http://localhost and run scripts/validate-demo.ps1."
} finally {
    if ($null -eq $previousProfile) {
        Remove-Item Env:APP_PROFILE -ErrorAction SilentlyContinue
    } else {
        $env:APP_PROFILE = $previousProfile
    }
}
