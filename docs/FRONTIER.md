# Frontier calibration

**Calibration date:** 2026-11-17. This is a desk review of public product documentation, not a hands-on comparative benchmark. Product performance figures below are vendor-published claims and have not been independently measured.

## Products and leading properties

| Product | Publicly documented lead | Evidence and limit |
| --- | --- | --- |
| FlightAware | Broad flight lookup and status tools, maps, airport activity, and alerts. FlightAware says its website data is typically about 30 seconds delayed, with updates usually one to four times per minute; its FAQ says displayed positions may lag by one to two minutes. | [Mobile tracker features](https://www.flightaware.com/mobile/), [data freshness FAQ](https://www.flightaware.com/about/faq/). These are FlightAware's published descriptions, not an independent feed measurement. |
| Flightradar24 | Global map, flight and airport details, lookup by flight number, callsign, route, or airline, flight history, and configurable alerts. It reports more than 50,000 receivers and over 250,000 flights tracked per day. | [App features](https://www.flightradar24.com/apps), [service and network overview](https://www.flightradar24.com/about), [feature guide](https://www.flightradar24.com/blog/product-features/). Scale numbers are vendor-reported. |
| Flighty | Trip-focused updates and alerts, inbound-aircraft context, connection assistance, and delay predictions. Its help page says it can monitor the inbound plane up to 25 hours before departure; its pricing page lists 65+ alerts for Pro and one flight free. | [Inbound plane feature](https://flighty.com/help/where-is-my-plane), [notification features](https://flighty.com/help/flighty-notifications), [plans](https://flighty.com/pricing). Feature availability can depend on platform and plan; prediction quality is not independently established here. |
| Cirium FlightStats | API-level status and position records, dated flight/route lookup, event deltas, and a push-based alert API. Cirium documents its status feed as updating its full data set about every six seconds on average, with ordinary flight records available roughly three days before departure through seven days after arrival. | [Flight Status API](https://developer.cirium.com/apis/flightstats-apis/flight-status), [status feed](https://developer.cirium.com/apis/flightstats-apis/flight-status-feed), [alerts](https://developer.cirium.com/apis/flightstats-apis/flight-alerts). The feed and some route/alert APIs are contract-plan products; these are vendor API statements, not an independent benchmark. |

## Properties used to judge this project

| Property | Frontier position | This project at this revision |
| --- | --- | --- |
| Flight lookup and identity | The products above accept several flight-search inputs and connect a traveler to an actual flight or aircraft. Public pages do not establish how each handles repeated flight numbers internally. | Candidate API preserves multiple AeroAPI results within the provider's available window; this UI does not request a date range. The visitor picks a returned route, and details and summary use the selected `fa_flight_id`. Focused service and UI tests cover this path. Full runtime confirmation is pending. |
| Freshness and source clarity | FlightAware publishes typical update delays; Flightradar24 and Flighty advertise live or real-time updates. Those descriptions are vendor claims and differ by source, platform, and plan. | The interface labels source and check time, and labels a position's timestamp/age. It is a snapshot lookup without automatic refresh; the live profile caches candidates for five minutes. The default demo is fixed fixture data. Live AeroAPI use, freshness, and account access are unverified. |
| Follow-up help | All three document alerts; Flighty additionally documents inbound aircraft context and delay predictions. | No saved flights, push alerts, prediction, map, or background monitoring. The visitor receives one lookup and an instance-bound summary. This is a substantial product gap. |
| Provenance | FlightAware publicly describes source aggregation and approximate freshness. Comparable provenance detail in every result was not verified across the other products. | Each result shows its source and check time; positions show source time and age. The display is implemented and component-tested, but the full backend-to-browser demo remains unverified. |
| Entry friction | FlightAware offers a web tracker and mobile apps; Flightradar24 offers web and mobile; Flighty is a trip-focused mobile product with plan-dependent features. | The intended one-time journey needs no account and has local sample buttons. Whether this is materially easier than current services has not been tested with travelers. |

## Position and gap

The strongest defensible difference is the implementation's explicit ambiguity handling: it asks which dated route the visitor means and keys the summary to that exact flight instance. That is a safeguard against an ident-only mismatch, not evidence of market novelty. Public product pages establish strong existing flight status, history, maps, alerts, and trip-following experiences. This project does not outperform those products on completeness, freshness, or follow-up support.

## Failure lessons and design response

- A status timestamp and a current-looking map are not the same as current aircraft position: FlightAware publishes a typical delay and possible position lag, while Cirium documents inferred position when an aircraft is out of receiver range. The interface therefore labels the source and check time and gives position age; live freshness remains unverified.
- A flight number alone is not a safe record key: FlightAware documents `fa_flight_id` as a unique identifier for one flight instance ([AeroAPI FAQ](https://www.flightaware.com/commercial/aeroapi/faq.rvt)). This project retains candidates and uses that ID for detail and summary paths instead of silently choosing the first row.
- A queue acknowledgement does not prove successful summary persistence. The read path has bounded polling and an explicit requeue action; a running Kafka/Postgres outage-and-recovery exercise is still required before any reliability claim.
- The four-service design adds local operational dependencies. It is retained to demonstrate the repository's portfolio architecture, not because capacity or user-outcome evidence shows it is better than a single service.

The largest outcome gap is that the value proposition is only exercised with fixtures and mocked provider boundaries. A real traveler cannot yet rely on this workspace as a demonstrated live tracker. The next highest-gain evidence is a clean Compose run with Redis/Kafka/Postgres, then a credentialed but budget-controlled provider contract check, followed by moderated traveler usability sessions. Performance and deployment claims remain unmade until those runs exist.
