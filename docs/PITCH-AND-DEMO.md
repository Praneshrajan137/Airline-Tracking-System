# Pitch and demo walkthrough

## Short pitch

Airline Tracker is a portfolio project for travelers and people following a flight. Look up a flight number, choose a returned dated route when several instances match, then see the selected instance’s reported status and a summary tied to its stable flight ID. Live search uses FlightAware’s ordinary available window and does not request an arbitrary date. This is a checked snapshot, not continuous tracking. The offline demo labels sample data, requires no account, and makes no external API calls.

The demonstrated difference is the explicit instance-selection step and instance-specific summary lookup. It is implemented in the candidate API, selection UI, and summary endpoint. Whether this is more useful than existing flight trackers has not been tested with travelers.

## Two-minute demo

1. Start the fixture-backed stack with `scripts/deploy.ps1` on Windows or `bash scripts/deploy.sh` on Unix. Wait for health checks and run the matching `validate-demo` smoke check before opening `http://localhost`.
   - Show guest access and the sample flight numbers.
2. Search `UAL123`.
   - The fixture returns two dated instances; explain that a flight number alone can be ambiguous and only returned matches appear.
3. Select either route.
   - Show airport-local schedule times, the provider/demo source, and when the record was checked.
   - If position data is present, show its reported timestamp and age rather than calling it live.
4. Read the summary.
   - The title says “Flight summary · demo”; the text says it is deterministic sample text and that no AI provider was called.
5. Search `BA178` to show the one-candidate path.

## Claims this walkthrough demonstrates

- The local demo permits search without registration.
- An ambiguous demo flight number can be resolved by selecting an instance.
- The selected candidate ID is used to request its summary.
- The demo identifies itself in the result and summary UI.
- A live data source, live summary model, traveler satisfaction, or production deployment is not demonstrated by this walkthrough.
