# Product requirements: Airline Tracker

**Status:** academic and portfolio scope, updated 2026-11-18. This document describes implemented behavior separately from product assumptions and unverified operating targets.

## Purpose and audience

The intended audience is travelers and people following a flight. The working hypothesis is that a person entering a flight number needs to identify the correct returned dated trip, then understand its reported route, timing, status, and a concise explanation. No traveler interviews or usability sessions have validated that hypothesis yet.

This is a checked snapshot lookup, not continuous flight tracking. The live query uses FlightAware's ordinary `/flights/{ident}` endpoint without a date-range parameter; FlightAware says ordinary endpoints generally return 48–72 hours by default, depending on the endpoint ([AeroAPI FAQ](https://www.flightaware.com/commercial/aeroapi/faq.rvt)). The UI can distinguish returned instances, but the visitor cannot request a specific date.

The experience must never silently attach a summary to the wrong flight instance, describe an old/missing position as live, or present local demo fixtures as provider data.

## Product behavior

1. A visitor can search without an account.
2. Candidate search preserves all dated instances returned by the provider within its available window. When several match, the visitor chooses the route and date.
3. A selected flight is identified by `fa_flight_id` for subsequent lookup and summary retrieval.
4. Route schedules use the origin and destination airport timezones when those are supplied. Missing zones fall back to device local time and the display identifies that fallback.
5. Results identify their source, service check time, and last-position provider timestamp when available.
6. Summary generation is requested only after the visitor selects a flight instance. The client polls for a bounded period and exposes a retry action afterward.
7. Demo mode uses local sample records and fixed summary text. It makes no external provider calls.
8. Live provider mode requires the relevant API credentials. Provider errors and rate limits remain errors; they are not presented as “flight not found.”

## Product frontier calibration

This is a capability comparison based on official product pages, not a performance benchmark or claim of superiority.

| Product | Demonstrated frontier property | Documented boundary for this project |
| --- | --- | --- |
| FlightAware | Flight status/tracking and a commercial AeroAPI intended for application integrations. [AeroAPI](https://www.flightaware.com/commercial/aeroapi/) | This project uses one provider path and has not tested its coverage, freshness, or license for any public/commercial use. |
| Flightradar24 | Broad flight tracking, playback/history, and flight detail features. [Product features](https://www.flightradar24.com/blog/product-features/) | This project has no map, playback, or historical-search experience. |
| Flighty | Traveler-oriented alerts and trip update experience. [Notifications](https://flighty.com/help/flighty-notifications) | This project has no account, saved itinerary, push alerts, or traveler usability evidence. |
| Cirium FlightStats | Flight status, status feeds, and alert APIs for integrations. [Status API](https://developer.cirium.com/apis/flightstats-apis/flight-status), [feed](https://developer.cirium.com/apis/flightstats-apis/flight-status-feed), [alerts](https://developer.cirium.com/apis/flightstats-apis/flight-alerts) | This project has not benchmarked coverage, latency, cost, or failure handling against Cirium. |

The one implemented distinction worth evaluating is explicit selection when a flight number maps to several instances, followed by summary retrieval using that exact instance ID. It is not established as novel or more useful than existing services. A traveler study and a feature audit could disprove the value of this distinction.

## Design decisions and alternatives

- **Keep four Spring services.** The project already uses registry, gateway, flight data, and summary services with Kafka between flight lookup and summary generation. Replacing them with a monolith would reduce local operations but would discard the event flow this portfolio project is meant to demonstrate. The cost is greater deployment complexity; this remains a portfolio choice, not a production-scale claim.
- **Use the stable provider flight ID.** A flight number alone can match multiple scheduled instances. Returning only the first candidate was rejected because it can show the wrong date or route. The compatibility ident-only API returns `409` when ambiguous.
- **Keep guest access.** Account creation is deferred because a one-time lookup does not need stored identity for the selected scope. Saved flights, alerts, and user-specific data remain outside scope.
- **Use a fixture profile.** A keyless demo helps reviewers reproduce the interface without paid or licensed provider traffic. Fixture data must remain visibly labeled.
- **Use bounded retries.** A missing summary is expected briefly after the Kafka event. Polling forever was rejected because it hides failure and spends resources; the UI retries for about 20 seconds, then offers manual retry.

## Failure cases the product must handle

- Multiple candidates, no candidates, invalid identifier, and selected ID not belonging to the candidate set.
- Provider 404, 429, 5xx, timeout, or open circuit, each with a distinct outcome.
- Redis outage: reject new provider calls rather than bypass the spend limiter.
- Kafka/database/OpenAI summary delay or failure: show a bounded pending state and retry control.
- Missing airport metadata or position timestamp: show unavailable/fallback labels rather than inventing values.
- Rapid consecutive searches: stale results must not overwrite the latest search.

## Acceptance and evidence status

| Requirement | Acceptance evidence | Current status |
| --- | --- | --- |
| Guest search and candidate selection | Frontend build; candidate API controller test; clean Compose walkthrough | Code is implemented; run evidence is in the project completion report. Compose runtime may be blocked by Docker availability. |
| Exact instance summary | Repository query by `fa_flight_id`; controller/API test with two instances | Implemented; see test evidence. |
| Accurate source and time labels | DTO deserialization tests and frontend build; source value must identify demo/live | Implemented; live source quality remains unverified. |
| No provider calls in demo | Mock test verifies no OpenAI call; fixture provider returns local rows | Implemented in code; a full Compose network trace is still required to prove runtime egress behavior. |
| Traveler usefulness and clarity | Five moderated traveler sessions and task completion/error notes | Unknown; participants were not available. |
| Distinct value versus leaders | Comparative usability study and current feature audit | Unknown; do not claim novelty or superiority. |
| p95 <500 ms cached; controlled miss <2 s; >95% success at 100 concurrent requests | Reproducible Gatling run against a started stack, including collected report and environment | Target only; not measured. |
| Deployable and recoverable in a real partner environment | Clean install, secrets, TLS, backup/restore, outage, and security review | Unverified; local compose is a portfolio demo. |

The five-session study materials are in [TRAVELER-RESEARCH.md](TRAVELER-RESEARCH.md). The protocol is ready; recruitment and all five sessions remain pending. No participant contact or research findings are represented here.

## Technology baseline and verification gates

- Java 25 LTS, Spring Boot 4.1.2, and Spring Cloud 2025.1.2 are the project baseline. Maven 3.9.16 is pinned by the root wrapper.
- The Maven reactor builds all four services, the Compose integration module, and the isolated Gatling module.
- Compose E2E uses WireMock provider endpoints on an internal network. The Gatling overlay uses labeled deterministic fixtures. Browser E2E intercepts API responses with test fixtures.
- Performance acceptance targets are cached lookup p95 `<500 ms`, controlled cache-miss p95 `<2 s`, and success `>95%` with 100 concurrent cached lookups. They remain targets until a report from a completed run is recorded.
- The gateway flight lookup burst is configurable with a default of 100. Its refill remains 1 request/second; the summary-route burst remains 5. FlightAware's existing application quotas are unchanged.

## Non-goals

Accounts and saved trips, push notifications, map rendering, flight history/playback, cross-provider fallback, airline operations use, public hosting, and commercial/B2C distribution.
