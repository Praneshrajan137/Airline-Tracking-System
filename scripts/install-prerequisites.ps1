# Install Prerequisites Script
# Run this script as Administrator: Right-click PowerShell -> "Run as Administrator"

Write-Host "=== Installing Prerequisites ===" -ForegroundColor Cyan
Write-Host ""

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator!" -ForegroundColor Red
    Write-Host "Please right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

Write-Host "✓ Running as Administrator" -ForegroundColor Green
Write-Host ""

# Check Chocolatey
Write-Host "Checking Chocolatey..." -ForegroundColor Yellow
try {
    $chocoVersion = choco --version
    Write-Host "✓ Chocolatey installed (version $chocoVersion)" -ForegroundColor Green
} catch {
    Write-Host "✗ Chocolatey not found. Installing..." -ForegroundColor Yellow
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    Write-Host "✓ Chocolatey installed" -ForegroundColor Green
}

Write-Host ""

# Install Java 17
Write-Host "Installing Java 17 (Eclipse Temurin)..." -ForegroundColor Yellow
try {
    choco install temurin17 -y --no-progress
    Write-Host "✓ Java 17 installed" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to install Java 17" -ForegroundColor Red
    Write-Host "  Error: $_" -ForegroundColor Red
}

Write-Host ""

# Install Maven
Write-Host "Installing Maven..." -ForegroundColor Yellow
try {
    choco install maven -y --no-progress
    Write-Host "✓ Maven installed" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to install Maven" -ForegroundColor Red
    Write-Host "  Error: $_" -ForegroundColor Red
}

Write-Host ""

# Refresh environment variables
Write-Host "Refreshing environment variables..." -ForegroundColor Yellow
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

Write-Host ""

# Verify installations
Write-Host "=== Verification ===" -ForegroundColor Cyan
Write-Host ""

# Check Java
Write-Host "Java:" -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -match "version ""17") {
        Write-Host "✓ Java 17+ installed" -ForegroundColor Green
        Write-Host "  $javaVersion" -ForegroundColor Gray
    } else {
        Write-Host "⚠ Java found but version may not be 17+" -ForegroundColor Yellow
        Write-Host "  $javaVersion" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ Java not found in PATH (may need to restart terminal)" -ForegroundColor Yellow
}

Write-Host ""

# Check Maven
Write-Host "Maven:" -ForegroundColor Yellow
try {
    $mavenVersion = mvn -version 2>&1 | Select-Object -First 1
    if ($mavenVersion -match "Apache Maven 3\.[89]") {
        Write-Host "✓ Maven 3.8+ installed" -ForegroundColor Green
        Write-Host "  $mavenVersion" -ForegroundColor Gray
    } else {
        Write-Host "⚠ Maven found but version may not be 3.8+" -ForegroundColor Yellow
        Write-Host "  $mavenVersion" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ Maven not found in PATH (may need to restart terminal)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Installation Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANT: Please restart your terminal/PowerShell for PATH changes to take effect." -ForegroundColor Yellow
Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

