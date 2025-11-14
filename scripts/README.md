# Deployment Scripts

Automated deployment scripts for the Airline Tracking System.

## Available Scripts

### 1. `deploy.sh` (Linux/Mac/WSL)

Bash script for Unix-based systems.

**Usage:**
```bash
# Make executable (first time only)
chmod +x scripts/deploy.sh

# Run deployment
./scripts/deploy.sh
```

### 2. `deploy.ps1` (Windows)

PowerShell script for Windows systems.

**Usage:**
```powershell
# Run deployment
.\scripts\deploy.ps1
```

## What These Scripts Do

### Step 1: Check Prerequisites ✅
- Verifies Docker is installed
- Verifies Docker Compose is installed
- Checks Docker daemon is running

### Step 2: Validate .env File ✅
- Confirms `.env` file exists
- Validates required environment variables:
  - `FLIGHTAWARE_API_KEY`
  - `OPENAI_API_KEY`
  - `POSTGRES_PASSWORD`
- Ensures no placeholder values remain

### Step 3: Build Docker Images 🔨
- Stops any existing containers
- Builds all Docker images with `--no-cache` flag
- Ensures clean builds every time

### Step 4: Start Services 🚀
- Starts all services with `docker-compose up -d`
- Services run in detached mode (background)

### Step 5: Wait for Health Checks ⏳
- Monitors health status of all services:
  - PostgreSQL
  - Redis
  - Kafka
  - Service Registry (Eureka)
  - API Gateway
  - FlightData Service
  - LLM Summary Service
- Maximum wait time: 2 minutes
- Checks every 5 seconds

### Step 6: Smoke Tests 🧪
Tests critical endpoints:
1. **Eureka Dashboard**: `http://localhost:8761`
2. **API Gateway Health**: `http://localhost:8080/actuator/health`
3. **Flight Data API**: `http://localhost:8080/api/v1/flight/UAL123`

Each test retries up to 3 times with 5-second delays.

### Step 7: Success Message 🎉
Displays:
- Service URLs
- Health check endpoints
- Example API calls
- Management commands
- Monitoring tips

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success - deployment complete |
| 1 | Failure - check error message |

## Error Handling

The scripts will exit immediately if:
- Docker/Docker Compose not installed
- Docker daemon not running
- `.env` file missing or invalid
- Image build fails
- Services fail to start
- Health checks timeout (2 minutes)

## Troubleshooting

### Script Fails at Prerequisites
```bash
# Linux/Mac
sudo apt-get install docker.io docker-compose  # Ubuntu/Debian
brew install docker docker-compose              # Mac

# Windows
# Download Docker Desktop from docker.com
```

### Script Fails at .env Validation
```bash
# Create .env from template
cp env.example .env

# Edit with your API keys
nano .env  # or vim, code, etc.
```

### Script Fails at Health Checks
```bash
# Check service logs
docker-compose logs -f

# Check specific service
docker-compose logs flightdata-service

# Restart services
docker-compose restart
```

### Services Won't Start
```bash
# Check Docker resources
docker system df

# Clean up
docker-compose down -v
docker system prune -a

# Try again
./scripts/deploy.sh
```

## Manual Deployment (Without Script)

If you prefer manual deployment:

```bash
# 1. Ensure .env exists
cp env.example .env
# Edit .env with your API keys

# 2. Build images
docker-compose build --no-cache

# 3. Start services
docker-compose up -d

# 4. Check status
docker-compose ps

# 5. View logs
docker-compose logs -f
```

## Environment-Specific Deployments

### Development
```bash
# Use default .env
./scripts/deploy.sh
```

### Production
```bash
# Use production .env
cp .env.production .env
./scripts/deploy.sh
```

### Testing with E2E Docker Compose
```bash
# For E2E tests (includes WireMock)
docker-compose -f docker-compose.e2e.yml up -d
```

## Updating Deployment

To update after code changes:

```bash
# Stop services
docker-compose down

# Rebuild and redeploy
./scripts/deploy.sh
```

## Monitoring After Deployment

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f flightdata-service

# Last 100 lines
docker-compose logs --tail=100 -f
```

### Check Service Status
```bash
docker-compose ps
```

### Resource Usage
```bash
docker stats
```

### API Usage (Rate Limiting)
```bash
# Linux/Mac
docker-compose logs -f flightdata-service | grep "usage"

# Windows
docker-compose logs -f flightdata-service | findstr "usage"
```

## CI/CD Integration

These scripts can be integrated into CI/CD pipelines:

### GitHub Actions
```yaml
- name: Deploy
  run: |
    chmod +x scripts/deploy.sh
    ./scripts/deploy.sh
```

### GitLab CI
```yaml
deploy:
  script:
    - chmod +x scripts/deploy.sh
    - ./scripts/deploy.sh
```

### Jenkins
```groovy
stage('Deploy') {
    steps {
        sh 'chmod +x scripts/deploy.sh'
        sh './scripts/deploy.sh'
    }
}
```

## Security Notes

⚠️ **Important:**
- Never commit `.env` file to version control
- Scripts validate API keys are not placeholders
- Deployment requires proper credentials in `.env`
- Scripts check for Docker daemon before proceeding

## Support

For issues:
1. Check script output for specific error
2. Review `DEPLOYMENT.md` for manual steps
3. Check `SECURITY.md` for API key issues
4. View logs: `docker-compose logs -f`

