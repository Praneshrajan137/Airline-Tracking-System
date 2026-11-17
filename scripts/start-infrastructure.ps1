# Compatibility entry point. This starts the complete local demo stack.
& (Join-Path $PSScriptRoot "deploy.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
