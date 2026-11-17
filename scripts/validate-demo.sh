#!/usr/bin/env bash
set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://localhost}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
TEST_FLIGHT="${1:-UAL123}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-20}"
CURL_TIMEOUT_SECONDS="${CURL_TIMEOUT_SECONDS:-10}"

for command in curl jq; do
  command -v "$command" >/dev/null 2>&1 || {
    printf 'Required command is missing: %s\n' "$command" >&2
    exit 1
  }
done

ident="$(printf '%s' "$TEST_FLIGHT" | tr '[:lower:]' '[:upper:]')"
curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$FRONTEND_URL/" >/dev/null
printf 'PASS  Frontend responds\n'

health="$(curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$GATEWAY_URL/actuator/health")"
printf '%s' "$health" | jq -e '.status == "UP"' >/dev/null
printf 'PASS  Gateway health is UP\n'

candidates="$(curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$GATEWAY_URL/api/v1/flight/$ident/candidates")"
count="$(printf '%s' "$candidates" | jq 'length')"
[[ "$count" -gt 0 ]] || { printf 'No candidates returned for %s\n' "$ident" >&2; exit 1; }
printf 'PASS  %s returns at least one candidate\n' "$ident"

source="$(printf '%s' "$candidates" | jq -r '.[0].data_source')"
[[ "$source" == "Demo fixture" ]] || {
  printf 'This check expects the demo profile; received source: %s\n' "$source" >&2
  exit 1
}
flight_id="$(printf '%s' "$candidates" | jq -r '.[0].fa_flight_id')"
printf 'PASS  Default demo profile is active; no provider call was needed\n'

flight="$(curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$GATEWAY_URL/api/v1/flight/$ident/instance/$flight_id")"
printf '%s' "$flight" | jq -e --arg id "$flight_id" '.fa_flight_id == $id' >/dev/null
printf 'PASS  Selected instance lookup preserves its ID\n'

summary_url="$GATEWAY_URL/api/v1/flight/$ident/summary/$flight_id"
summary="$(curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$summary_url" 2>/dev/null || true)"
if [[ -z "$summary" ]]; then
  curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS -X POST "$GATEWAY_URL/api/v1/flight/$ident/instance/$flight_id/summary/retry" >/dev/null
  printf 'PASS  Selected-instance summary request is queued\n'
  for ((second = 0; second < TIMEOUT_SECONDS; second++)); do
    sleep 1
    summary="$(curl --max-time "$CURL_TIMEOUT_SECONDS" -fsS "$summary_url" 2>/dev/null || true)"
    [[ -n "$summary" ]] && break
  done
fi

printf '%s' "$summary" | jq -e --arg id "$flight_id" '.fa_flight_id == $id and (.summary_text | startswith("Demo summary:"))' >/dev/null || {
  printf 'No matching demo summary appeared within %s seconds\n' "$TIMEOUT_SECONDS" >&2
  exit 1
}
printf 'PASS  Summary is demo text tied to the selected instance\n'
printf 'Local demo smoke check completed.\n'
