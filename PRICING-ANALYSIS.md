# Provider cost and license status

No current monthly cost or quota estimate is supported by this repository. The default demo does not call FlightAware or OpenAI.

Live provider use requires checking the account’s current plan, API entitlements, applicable license, model access, pricing, and usage limits at the time of use:

- [FlightAware AeroAPI](https://www.flightaware.com/commercial/aeroapi/)
- [OpenAI API models](https://developers.openai.com/api/docs/models)

The FlightAware service has Redis-backed request counters and the gateway has a Redis-backed client token bucket. These controls limit requests; they do not establish billing, guarantee a monthly ceiling, or replace provider budget alerts. No live paid request was made during this work.

Before enabling live mode, record the approved provider plan and intended academic use, set conservative limits, confirm alerts, and verify that the configured model and API request work with the intended account. Public or commercial use is outside this project’s approved scope.
