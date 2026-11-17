$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

docker compose down
if ($LASTEXITCODE -ne 0) { throw "The local Compose stack did not stop cleanly." }
Write-Host "Local demo stopped. Named volumes were preserved."
