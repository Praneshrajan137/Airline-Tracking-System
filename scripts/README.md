# Local project scripts

These scripts support the local fixture-backed demo. They are not a public deployment system.

## Start and stop

From the repository root on Windows:

```powershell
.\scripts\deploy.ps1
.\scripts\stop-infrastructure.ps1
```

On Linux, macOS, or WSL, from the repository root:

```bash
bash scripts/deploy.sh
docker compose down
```

The launcher forces `APP_PROFILE=demo` for the Compose command, validates the Compose file, and starts the stack. It does not tear down an existing stack first. Stopping with `docker compose down` preserves named volumes; `down -v` removes local database, broker, and monitoring data.

`start-infrastructure.ps1` is a compatibility wrapper for the same complete demo stack; it does not start infrastructure alone.

## Demo smoke checks

After the stack is up, `validate-demo.ps1` checks the frontend, gateway health, fixture source, selected flight ID, and matching demo summary. `validate-demo.sh` performs the same checks and requires `curl` and `jq`. Both target only the local demo and may queue the selected fixture's summary if it is not ready yet.

## Other files

`init-db.sql` is a manual schema helper, not part of the Compose startup path. No usage-monitor script or billable-cost estimate is provided. Provider quotas restrict request counts; they do not measure account charges or guarantee a monthly spend ceiling.

For the supported walkthrough, see [Quick Start](../QUICK-START.md). Deployment constraints and recovery steps are in [DEPLOYMENT.md](../DEPLOYMENT.md) and [the runbook](../docs/RUNBOOK.md).
