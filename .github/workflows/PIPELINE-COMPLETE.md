# CI pipeline

`.github/workflows/ci-cd.yml` uses blocking quality gates for:

1. Java 25 root-reactor build and service tests.
2. Full-stack Compose E2E, including selected-flight behavior and provider rate-limit recovery through WireMock.
3. Gatling p95 and success-rate assertions against the fixture-only Compose overlay.
4. Playwright browser journey against deterministic API fixtures.

Reports and Compose diagnostics are uploaded as workflow artifacts. Service images are built and pushed to GHCR only on a successful `main` push after all gates pass. `.github/workflows/ci-simple.yml` provides a faster Java compile plus frontend unit/build check.

A workflow definition does not prove a gate has passed. Check the linked run and its artifacts before describing a build, browser flow, recovery scenario, or latency target as verified. These checks use mocks and local fixtures; they make no paid/live provider requests and establish no production-readiness claim.