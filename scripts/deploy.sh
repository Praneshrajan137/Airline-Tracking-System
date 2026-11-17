#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

command -v docker >/dev/null 2>&1 || { printf 'Docker is required.\n' >&2; exit 1; }
docker compose version >/dev/null
docker info >/dev/null

# Force this helper to use local fixtures even if .env selects another profile.
APP_PROFILE=demo docker compose config --quiet
APP_PROFILE=demo docker compose up --build -d
docker compose ps

printf '\nLocal demo containers launched. Wait for health checks, then open http://localhost and run scripts/validate-demo.sh.\n'
