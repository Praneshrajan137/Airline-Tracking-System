# Quick start

This is an academic portfolio demo. The default profile uses local flight fixtures and does not call FlightAware or OpenAI.

1. Install Docker Desktop with Compose.
2. From the repository root, run:

   ```powershell
   if (!(Test-Path .env)) { Copy-Item env.example .env } else { Write-Host ".env already exists; keeping it." }
   .\scripts\deploy.ps1
   ```

3. Wait for Compose health checks, then run `pwsh -File scripts/validate-demo.ps1`.
4. Open [http://localhost](http://localhost).
5. Search `UAL123` and choose one of its two flight instances; try `BA178` for the one-result path.

The frontend, gateway, and local monitoring interfaces bind to loopback. The API is available at `http://localhost:8080`; internal service and infrastructure ports are not published. Prometheus, Grafana, and Zipkin use ports 9090, 3000, and 9411 by default.

For live provider use, read the scope and credential requirements in [README.md](README.md) and current limitations in the [runbook](docs/RUNBOOK.md). Live provider behavior, costs, and traveler usefulness have not been verified.
