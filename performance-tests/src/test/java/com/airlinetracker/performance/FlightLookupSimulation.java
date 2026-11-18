package com.airlinetracker.performance;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.core.Simulation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.http.HttpDsl.http;

/** Local synthetic gate. The Compose performance overlay enables deterministic, labeled fixtures. */
public class FlightLookupSimulation extends Simulation {
    private static final String BASE_URL = System.getProperty(
            "target.baseUrl", System.getenv().getOrDefault("PERF_TARGET_URL", "http://localhost:8080"));
    private static final String CACHE_WARM_PATH = "/api/v1/flight/UAL123/candidates";

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .userAgentHeader("AirlineTracker-Gatling/1.0");

    private final ScenarioBuilder controlledMiss = scenario("Controlled cache miss")
            .exec(http("controlled-miss")
                    .get("/api/v1/flight/BA178/candidates")
                    .check(io.gatling.javaapi.http.HttpDsl.status().is(200)))
            .pause(Duration.ofSeconds(2));

    private final ScenarioBuilder cachedLookups = scenario("100 concurrent cache hits")
            .exec(http("cache-hit")
                    .get(CACHE_WARM_PATH)
                    .check(io.gatling.javaapi.http.HttpDsl.status().is(200)));

    public FlightLookupSimulation() {
        setUp(
                controlledMiss.injectOpen(atOnceUsers(1))
                        .andThen(cachedLookups.injectOpen(atOnceUsers(100)))
        ).protocols(httpProtocol)
                .assertions(
                        details("controlled-miss")
                                .responseTime().percentile3().lt(2_000),
                        details("cache-hit")
                                .responseTime().percentile3().lt(500),
                        global().successfulRequests().percent().gt(95.0)
                );
    }

    @Override
    public void before() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + CACHE_WARM_PATH))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Could not warm demo cache: HTTP " + response.statusCode());
            }
            // Refill the one gateway token consumed by warming before the 100-user burst.
            Thread.sleep(1_100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while warming the cache", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Performance target is not ready at " + BASE_URL, exception);
        }
    }
}
