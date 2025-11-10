# Installation Guide

## Prerequisites

Before starting development, ensure you have the following installed:

- **Java 17+** (LTS recommended)
- **Maven 3.8+**
- **Docker Desktop** (for local infrastructure: PostgreSQL, Redis, Kafka)
- **Git** (for version control)

---

## 1. Install Java 17

### Option A: Using Chocolatey (Recommended for Windows)

```powershell
# Install Chocolatey if not already installed
# Run PowerShell as Administrator, then:
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Install Java 17 (Eclipse Temurin - OpenJDK)
choco install temurin17 -y

# Verify installation
java -version
```

**Expected Output:**
```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x+xx (build 17.0.x+xx)
OpenJDK 64-Bit Server VM Temurin-17.0.x+xx (build 17.0.x+xx, mixed mode, sharing)
```

### Option B: Manual Installation

1. **Download Java 17:**
   - Visit: https://adoptium.net/temurin/releases/?version=17
   - Download: **Windows x64 JDK** (`.msi` installer)

2. **Install:**
   - Run the downloaded `.msi` installer
   - Follow installation wizard
   - **Important:** Check "Add to PATH" during installation

3. **Set JAVA_HOME:**
   ```powershell
   # Find Java installation path (usually: C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot)
   # Set JAVA_HOME environment variable:
   [System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Eclipse Adoptium\jdk-17.0.12+7-hotspot', 'User')
   
   # Add to PATH
   $currentPath = [System.Environment]::GetEnvironmentVariable('Path', 'User')
   [System.Environment]::SetEnvironmentVariable('Path', "$currentPath;$env:JAVA_HOME\bin", 'User')
   ```

4. **Verify:**
   ```powershell
   java -version
   javac -version
   ```

---

## 2. Install Maven 3.8+

### Option A: Using Chocolatey

```powershell
# Install Maven
choco install maven -y

# Verify installation
mvn -version
```

**Expected Output:**
```
Apache Maven 3.9.x (xxxxx)
Maven home: C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.x
Java version: 17.0.x, vendor: Eclipse Adoptium
Java home: C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot
Default locale: en_US, platform encoding: Cp1252
OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"
```

### Option B: Manual Installation

1. **Download Maven:**
   - Visit: https://maven.apache.org/download.cgi
   - Download: **apache-maven-3.9.x-bin.zip**

2. **Extract:**
   - Extract to: `C:\Program Files\Apache\maven` (or `C:\apache-maven`)

3. **Set Environment Variables:**
   ```powershell
   # Set M2_HOME
   [System.Environment]::SetEnvironmentVariable('M2_HOME', 'C:\Program Files\Apache\maven', 'User')
   
   # Set MAVEN_HOME (some tools use this)
   [System.Environment]::SetEnvironmentVariable('MAVEN_HOME', 'C:\Program Files\Apache\maven', 'User')
   
   # Add to PATH
   $currentPath = [System.Environment]::GetEnvironmentVariable('Path', 'User')
   [System.Environment]::SetEnvironmentVariable('Path', "$currentPath;C:\Program Files\Apache\maven\bin", 'User')
   ```

4. **Verify:**
   ```powershell
   mvn -version
   ```

---

## 3. Install Docker Desktop

### Download and Install

1. **Download:**
   - Visit: https://www.docker.com/products/docker-desktop/
   - Download: **Docker Desktop for Windows**

2. **Install:**
   - Run the installer
   - Follow installation wizard
   - Restart computer if prompted

3. **Verify:**
   ```powershell
   docker --version
   docker-compose --version
   ```

**Expected Output:**
```
Docker version 24.x.x, build xxxxx
docker-compose version 1.29.x, build xxxxx
```

---

## 4. Verify All Prerequisites

Run this verification script:

```powershell
Write-Host "=== Prerequisites Check ===" -ForegroundColor Cyan

# Check Java
Write-Host "`nJava:" -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    if ($javaVersion -match "version ""17") {
        Write-Host "✓ Java 17+ installed" -ForegroundColor Green
        Write-Host "  $javaVersion" -ForegroundColor Gray
    } else {
        Write-Host "✗ Java 17+ required (found: $javaVersion)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Java not found" -ForegroundColor Red
}

# Check Maven
Write-Host "`nMaven:" -ForegroundColor Yellow
try {
    $mavenVersion = mvn -version 2>&1 | Select-Object -First 1
    if ($mavenVersion -match "Apache Maven 3\.[89]") {
        Write-Host "✓ Maven 3.8+ installed" -ForegroundColor Green
        Write-Host "  $mavenVersion" -ForegroundColor Gray
    } else {
        Write-Host "✗ Maven 3.8+ required (found: $mavenVersion)" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Maven not found" -ForegroundColor Red
}

# Check Docker
Write-Host "`nDocker:" -ForegroundColor Yellow
try {
    $dockerVersion = docker --version
    Write-Host "✓ Docker installed" -ForegroundColor Green
    Write-Host "  $dockerVersion" -ForegroundColor Gray
} catch {
    Write-Host "✗ Docker not found" -ForegroundColor Red
}

# Check Git
Write-Host "`nGit:" -ForegroundColor Yellow
try {
    $gitVersion = git --version
    Write-Host "✓ Git installed" -ForegroundColor Green
    Write-Host "  $gitVersion" -ForegroundColor Gray
} catch {
    Write-Host "✗ Git not found" -ForegroundColor Red
}

Write-Host "`n=== End Check ===" -ForegroundColor Cyan
```

---

## 5. Troubleshooting

### Java Not Found After Installation

1. **Restart Terminal/PowerShell** (environment variables need refresh)

2. **Verify JAVA_HOME:**
   ```powershell
   $env:JAVA_HOME
   ```

3. **Verify PATH:**
   ```powershell
   $env:Path -split ';' | Select-String -Pattern 'java|jdk'
   ```

4. **Manual PATH Update:**
   - Open **System Properties** → **Environment Variables**
   - Add `%JAVA_HOME%\bin` to **Path** variable

### Maven Not Found After Installation

1. **Restart Terminal/PowerShell**

2. **Verify M2_HOME:**
   ```powershell
   $env:M2_HOME
   ```

3. **Verify PATH:**
   ```powershell
   $env:Path -split ';' | Select-String -Pattern 'maven'
   ```

### Docker Desktop Not Starting

1. **Enable WSL 2:**
   - Windows Features → Enable "Windows Subsystem for Linux"
   - Enable "Virtual Machine Platform"

2. **Update WSL:**
   ```powershell
   wsl --update
   ```

3. **Restart Docker Desktop**

---

## Next Steps

Once all prerequisites are installed:

1. **Clone/Setup Project:**
   ```powershell
   cd "C:\Users\Pranesh\OneDrive\Music\AIRLINE TRACKING SYSTEM\airline-tracker-system"
   ```

2. **Start Infrastructure:**
   ```powershell
   docker-compose up -d
   ```

3. **Verify Infrastructure:**
   ```powershell
   docker ps
   ```

4. **Build Services:**
   ```powershell
   mvn clean install
   ```

---

## Quick Install Script (Chocolatey)

If you have Chocolatey installed, run this single command:

```powershell
choco install temurin17 maven docker-desktop git -y
```

Then restart your terminal and verify with the check script above.

