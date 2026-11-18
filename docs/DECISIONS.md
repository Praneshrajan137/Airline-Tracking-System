# Current product decisions

Updated 2026-11-18. This replaces the prior decision draft, which contained unsupported provider pricing, uptime, model, and production claims.

## Audience and distribution

**Decision:** Aim the checked snapshot lookup at travelers and people following flights, as an academic and portfolio project.

**Reason:** This is the user’s selected direction. It is not evidence that travelers have validated the problem, nor does it authorize public or commercial use of FlightAware data. Audience fit remains unknown until usability sessions are run.

## Architecture

**Decision:** Keep the four Spring services: service registry, gateway, flight data, and summary.

**Reason:** The repository is an academic microservices demonstration with event-based summary generation. A monolith could reduce local operations, but would remove the architecture the project is intended to show. No load or scale result proves that four services improve user outcomes.

## Java platform and build

**Decision:** Use Java 25 LTS, Spring Boot 4.1.2, Spring Cloud 2025.1.2, a root Maven reactor, and a Maven 3.9.16 wrapper.

**Reason:** This establishes one reproducible Java baseline across all four services and their test modules. The Boot 4 migration updates modular web, Kafka, and test starters. Java 25 runtime verification remains pending until a JDK 25 build completes.

## Verification gates

**Decision:** Require a full-stack WireMock Compose recovery suite, a deterministic Playwright browser journey, and a Gatling cache/burst gate in CI. Keep local demo and performance runs fixture-only.

**Reason:** These checks exercise the user flow and failure/load boundaries without making paid provider calls. Their acceptance limits are not production SLO claims; the Compose, browser, and Gatling reports must be retained as evidence.

## Traveler research

**Decision:** Prepare five moderated usability sessions and do not contact participants without explicit authorization.

**Reason:** Five sessions are the selected initial qualitative sample. The task guide and measures are ready in [TRAVELER-RESEARCH.md](TRAVELER-RESEARCH.md); findings stay pending until sessions occur.

## Flight identity

**Decision:** Search produces all candidates; selection and summary lookup use the stable `fa_flight_id`.

**Reason:** Flight numbers can refer to multiple dated instances. Returning the first candidate can display a different route or date from the traveler’s intent. Ident-only compatibility routes return conflict on known ambiguity.

## Summary generation and provider spend

**Decision:** Search does not generate summaries. The browser requests a summary only for the candidate the visitor selected.

**Reason:** Generating a summary for every candidate makes anonymous search cause unnecessary OpenAI work, and repeated cache misses can refresh provider timestamps. A selected-instance request keeps processing tied to demonstrated user intent. Redis-backed OpenAI quotas also limit request volume; they are guardrails, not a cost guarantee.

## Access and accounts

**Decision:** A one-time search requires no account. Account flows are deferred.

**Reason:** The selected scope has no saved trip, alert, or profile behavior that requires identity. Keeping the sign-in gate added friction without serving the current journey.

## Demo and live providers

**Decision:** Default local Compose profile is fixture-based and makes no provider requests. Live mode is opt-in and requires keys.

**Reason:** A reviewer can reproduce the interface without provider access or paid calls. The source label must distinguish fixtures from provider data.

## Summary retry and duplicate events

**Decision:** The UI polls a missing summary for a bounded period; manual retry queues the selected instance again. Duplicate events with an unchanged source check time are ignored.

**Reason:** A summary is asynchronous. A read-only retry cannot recover a lost Kafka event; explicit requeue is required. The source timestamp prevents duplicate provider calls while allowing a newer fetched record to refresh the summary.

## Provider quotas and model

**Decision:** Keep a global Redis quota around FlightAware calls and a per-client gateway token bucket. Use a configurable OpenAI model default of `gpt-6-luna`; demo text does not invoke a model.

**Reason:** Redis failures must not bypass spend controls. The model is configuration, not a verified account entitlement. Live API access, model parameters, provider costs, and quotas must be checked with valid credentials before live use.
