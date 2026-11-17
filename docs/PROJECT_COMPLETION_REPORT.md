# Project completion report

**Review date:** 2026-11-17
**Project:** Airline Tracker
**Assessment:** Implemented as a local portfolio demo with a tested guest journey. Full composed runtime, live provider behavior, traveler need, and production readiness remain unverified.

## Results and evidence

The product now has a no-account demo journey: search a flight number, choose among dated candidate instances when needed, see source and time context, and request a summary bound to the chosen flight's stable ID. The demo uses named fixtures and deterministic summary text; it does not require or call FlightAware or OpenAI.

Evidence gathered in this workspace:

- Frontend: **4 tests passed across 2 files**. The UI tests select a second `UAL123` instance, observe the initial summary-unavailable state, retry that exact instance, verify that no summary request is queued before selection, and check that both fixture rows are labeled before selection. API tests cover aborting a request at its deadline and a response body that stalls after headers.
- Frontend production build: `tsc -b && vite build` passed on Vite 6.4.6.
- Service registry: **5 tests passed** in the module test run.
- Flight-data service: **19 focused tests passed** across provider parsing, dated demo fixtures, service, controller, and quota enforcement.
- Summary service: **45 focused tests passed** across provider request handling, selected-instance retrieval, controller, service, repository, global quota enforcement, Kafka retry policy, JSON DLT serialization, topic/partition routing, failed DLT-send handling, and inherited consumer/producer security properties.
- API gateway: **5 tests passed**, covering configured routing and guest/protected route behavior.
- Both `docker compose config --quiet` and `docker compose -f integration-tests/docker-compose.e2e.yml config --quiet` passed.
- A Compose override check resolved `KAFKA_TOPIC_FLIGHT_DATA=airline-demo-events` to the same topic in both services and passed `KAFKA_TOPIC_PARTITIONS=4` plus `KAFKA_TOPIC_REPLICATION_FACTOR=2` to the DLT service.
- Integration test Java sources compiled with `mvn -B -ntp -DskipTests test-compile`; the E2E suite did not run.
- A concurrent run once timed out a FlightAware WireMock test. Its isolated rerun passed, and both WireMock suites now choose ephemeral ports to avoid conflicts between Maven modules.
- Docker runtime inspection failed because the Docker Desktop Linux engine pipe was unavailable. No images were built and no Compose service was started. These results do not prove end-to-end behavior.
- The browser host was unavailable for visual inspection. No live-browser, device-size, or accessibility audit was completed.

No provider key values were read and no paid/live provider requests were made. The test counts above describe focused or named suites, not total repository coverage. No coverage percentage, latency, uptime, capacity, or cost result is claimed.

## Reproduction

Run from the repository root unless a service directory is shown:

```powershell
cd frontend
npm ci
npm test
npm run build
```

```powershell
cd services/service-registry
mvn -B -ntp test
cd ../flightdata-service
mvn -B -ntp "-Dtest=FlightAwareClientTest,DemoFlightFixturesTest,FlightDataServiceTest,FlightControllerTest,RateLimitConfigTest" test
cd ../llm-summary-service
mvn -B -ntp "-Dtest=OpenAIClientTest,OpenAIRateLimitConfigTest,KafkaConsumerConfigTest,SummaryControllerTest,SummaryServiceTest,FlightSummaryRepositoryTest" test
cd ../api-gateway
mvn -B -ntp test
```

```powershell
cd <repository-root>
docker compose config --quiet
docker compose -f integration-tests/docker-compose.e2e.yml config --quiet
cd integration-tests
mvn -B -ntp -DskipTests test-compile
cd ..
.\scripts\deploy.ps1
```

The local launcher and full Compose E2E run require Docker Desktop and were **not** executed successfully in this environment. After the demo starts, the intended local walkthrough is in [PITCH-AND-DEMO.md](PITCH-AND-DEMO.md); operations and recovery steps are in [RUNBOOK.md](RUNBOOK.md).

## Seven-standard scorecard

“At start” describes what the repository claimed before this implementation; those claims were not accepted as proof. “At end” records the evidence and limits now available.

| Standard | At start | At end | Evidence and how to reproduce it | Status |
| --- | --- | --- | --- | --- |
| 1. Genuine, non-trivial, working | Described as production-ready and fully operational, with unsupported metrics. | Real candidate parsing, dated-instance selection, ID-bound summary retrieval, demo fixtures, quota enforcement, and bounded Kafka recovery are implemented. Focused component tests and a UI journey pass; composed runtime has not run. | Reproduce the service and frontend commands above. Clean Compose run is still required to prove the whole product works together. | Estimated — components verified; end-to-end unverified |
| 2. End to end; problem, audience, value clear | Claimed complete end-to-end operation without reproducible evidence. | First-time copy explains guest lookup and ambiguity; sample inputs, pending/error/retry states, and source labels exist. The stated value is selecting the intended dated flight and seeing its instance-specific summary. | `cd frontend; npm test; npm run build`; inspect [PITCH-AND-DEMO.md](PITCH-AND-DEMO.md). Full service journey awaits Docker. | Estimated |
| 3. Real problem and audience, addressed | Presented broad traveler value as established. | The intended audience is a traveler or flight follower who encounters repeated flight numbers. This is a product hypothesis; no traveler interviews or observed usability sessions were available. | Validate with moderated sessions and compare task completion/confusion against FlightAware, Flightradar24, and Flighty. Current evidence is in [PRD.md](PRD.md) and [FRONTIER.md](FRONTIER.md). | Unverified |
| 4. Complete, coherent experience | Claimed comprehensive feature coverage and healthy deployment. | Search, candidate choice, details, summary pending/error/retry, and pre-selection demo labeling have implementation coverage. Cross-service recovery, visual/browser/device states, and accessibility have not been demonstrated in a running app. | UI journey test and build commands above; then run the documented Compose demo and exercise empty, invalid, unavailable, and recovery cases. | Estimated |
| 5. Creative, novel, different | Claimed technical achievements without comparative evidence. | Selecting a dated candidate and binding the summary to `fa_flight_id` is a concrete ambiguity safeguard. It is not established as novel or more useful than existing products. | Read [FRONTIER.md](FRONTIER.md), which names four products and distinguishes vendor claims from independent measurements. Novelty and advantage need broader comparison and user evidence. | Unverified |
| 6. Finished and polished | Reported 100% coverage and “A+” polish without evidence. | Product copy, local demo, docs, request errors, time formatting fallback, summary retry, and focused UI behavior were revised. No visual QA in a live browser, accessibility audit, or composed acceptance run occurred. | Frontend test/build and Compose runbook above; visual and assistive-technology review remain follow-up work. | Estimated |
| 7. Deployable, scalable, reproducible | Claimed production readiness, scale, security, CI/CD, and monitoring. | Multi-stage Dockerfiles build from repository context and set a non-root Java user; Compose YAML validates; app and monitoring ports bind to loopback and the launcher forces fixtures. JSON DLT publishing, Kafka consumer/producer security-property inheritance, and a configurable DLT topic declaration are configured. Image build/start, clean-machine reproducibility, capacity, backup restore, TLS, and production security are unverified. | Both Compose config checks passed. Docker image build/start and realistic operations/load/security exercises are still required. | Unverified |
| Preserved behavior | Prior report claimed broad existing API behavior, without a reliable baseline. | Legacy single-flight lookup remains alongside candidate and selected-instance routes. Gateway permits intended guest flight routes and denies auth/unknown routes. Focused controller/gateway tests exercise these behaviors. | Run `FlightControllerTest` in the flight-data command and `mvn -B -ntp test` in `services/api-gateway`. Live compatibility with external clients is not established. | Verified in tested route scope |

## Claims ledger

The detailed ledger is maintained in [CLAIMS-LEDGER.md](CLAIMS-LEDGER.md). Its status is:

- **Proven in code and focused tests:** candidate rows are retained and visibly labeled before selection; a visitor can select an instance; selected summary requests use its stable ID; source/check/position time context is present; gateway guest routes are allowed and unrelated routes are denied. The Kafka serializer emits a JSON FlightData value and the DLT topic declaration uses the configured partition count.
- **Implemented, runtime not yet proven:** no-account local demo with clearly labeled fixtures; full backend-to-browser flow; compose-based service recovery.
- **Unverified:** live FlightAware credentials and response contract; OpenAI model/account entitlement and real request; user need and usability; comparative novelty; latency, availability, scale, cost, and production readiness.
- Prior claims of “production ready,” “real-time,” a 31 ms response, 99.9% uptime, 100 concurrent users, 90% test coverage, and a monthly provider cost were removed from current product copy because evidence did not support them.

## Frontier position

The calibration in [FRONTIER.md](FRONTIER.md) is a desk review of public product material, not a hands-on benchmark. The named products and published strengths are:

| Product | Publicly documented strength used for comparison | This project's demonstrated position |
| --- | --- | --- |
| FlightAware | Broad tracker and alerts; vendor FAQ describes typical delay and position lag. | Uses its API integration when configured, but no live request or freshness measurement was made. |
| Flightradar24 | Global map, lookup, history, and alerts; network/volume figures are vendor-published. | No map, history, or alerts. |
| Flighty | Trip-focused notifications, inbound-aircraft context, and delay predictions. | No saved trip, inbound monitoring, prediction, or push alert. |
| Cirium FlightStats | Status API, dated records, feed, and alerts; its feed update interval is vendor-published. | No feed, event subscription, or alert service. |

Across lookup identity, provenance, freshness, follow-up, and entry friction, the product currently demonstrates only local guest lookup, explicit candidate selection, an ID-bound summary, and source/time labels. It does not demonstrate superiority on live data, feature depth, or traveler outcomes. See [FlightAware freshness FAQ](https://www.flightaware.com/about/faq/), [Flightradar24 product features](https://www.flightradar24.com/apps), [Flighty notifications](https://flighty.com/help/flighty-notifications), and [Cirium Flight Status Feed](https://developer.cirium.com/apis/flightstats-apis/flight-status-feed). These are vendor descriptions; no independent competitor measurement was performed.

## Panel verdicts

- **Architecture and operations:** setup guides preserve an existing `.env`; launchers force the fixture profile; Compose validates; Java containers run as non-root; and external test ports bind to loopback. The shared Kafka topic setting now aligns producer and consumer, while only the DLT is auto-declared; managed Kafka permission and partition requirements are documented. No deployment, HA, backup, alerting, or capacity evidence exists; Docker runtime remains unavailable.
- **Domain and first-time user:** each candidate row now exposes its data source before selection, and the action is labeled "Search Flight." The journey reads as a checked snapshot and demo fixture. Audience value and live provider semantics remain unverified without user sessions and a running stack.
- **Adversarial review:** loopback-only E2E ports, request-body deadlines, provider error-body removal, bounded retry policy, and DLT recovery were reviewed. The DLT serializer and topic-partition gap was fixed with a dedicated JSON producer, send-result failure handling, and a DLT-only topic declaration; focused tests verify serializer output, DLT topic configuration, nonzero source partition routing, surfaced send failures, and inherited consumer/producer security properties. A broker-backed exhausted-event test remains unrun. Managed Kafka must grant topic-admin permissions or pre-provision the DLT; startup alone does not prove DLT readiness. Public use remains blocked by the shared anonymous quota: one visitor can consume capacity for all visitors, so this demo is not safe for an open public deployment.

The panel's remaining runtime and production limitations are recorded as unknowns below, not treated as accepted production readiness.

## Prioritized unknowns and next evidence

1. **Composed runtime and DLT delivery:** restore Docker Desktop; build and start the stack from a clean checkout; exercise candidate selection, summary persistence/retry, and recovery after Redis/Kafka/Postgres interruptions. Verify the DLT exists with at least as many partitions as the source and grant topic-admin permissions or pre-provision it. Force a permanent or exhausted provider failure, then verify the JSON record reaches the matching-partition `.DLT` and a failed DLT send leaves the original record recoverable. This can falsify the current assumption that the new service boundaries and Kafka recovery work together.
2. **Traveler value:** observe at least five target users completing a real "which flight is this and what is its status?" task. Record wrong selections, time, and whether the instance-specific summary helps. This can falsify the audience/value hypothesis.
3. **Live provider contracts and freshness:** use separately approved, budget-controlled credentials; check current FlightAware and OpenAI terms, model access, response fields, and timestamps. No live calls were authorized or made for this implementation.
4. **Security and abuse:** test the gateway with direct service exposure, spoofed forwarding headers, concurrent quota requests, malformed identifiers, and unavailable Redis. Confirm network boundaries from a running Compose deployment; current tests do not establish production security.
5. **Operations and capacity:** measure realistic lookup and summary workloads, logs/metrics/alerts, restart behavior, backup/restore, and dependency failure recovery before making any availability, scaling, or deployment claim.
6. **Product gap:** assess whether travelers value the candidate-selection safeguard after comparing the actual workflow against existing trackers. If not, revisit the product direction rather than polishing an unsupported differentiator.

## What remains incomplete

This implementation improves the portfolio demo and establishes focused code-level evidence. It does **not** establish that the composed application starts, that live providers work, that travelers need this product, that the flow is novel, or that it is safe and economical to deploy to the public. Those are open findings, not completed acceptance criteria.
