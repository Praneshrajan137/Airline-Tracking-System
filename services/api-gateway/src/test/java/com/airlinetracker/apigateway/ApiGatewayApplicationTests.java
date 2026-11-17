package com.airlinetracker.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.WebFilterChainProxy;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebFilterChainProxy securityFilterChain;

    @Test
    void contextLoads() {
        assertThat(routeLocator).isNotNull();
    }

    @Test
    void routesFlightAndSummaryRequestsToTheirServices() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).anySatisfy(route -> {
            assertThat(route.getId()).isEqualTo("flightdata-service-route");
            assertThat(route.getUri().toString()).contains("lb://FLIGHTDATA-SERVICE");
        });
        assertThat(routes).anySatisfy(route -> {
            assertThat(route.getId()).isEqualTo("flight-summary-service-route");
            assertThat(route.getUri().toString()).contains("lb://LLM-SUMMARY-SERVICE");
        });
    }

    @Test
    void bothPublicRoutesUseLoadBalancingAndAddressRateLimiting() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull().hasSize(2);
        routes.forEach(route -> {
            assertThat(route.getUri().getScheme()).isEqualTo("lb");
            assertThat(route.getFilters()).hasSize(1);
        });
    }

    @Test
    void summaryRouteMatchesLegacyAndInstanceSpecificPaths() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        var summaryRoute = routes.stream()
                .filter(route -> route.getId().equals("flight-summary-service-route"))
                .findFirst().orElseThrow();

        var legacySummary = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/flight/UAL123/summary").build());
        var instanceSummary = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/flight/UAL123/summary/fa-flight-current").build());

        assertThat(Mono.from(summaryRoute.getPredicate().apply(legacySummary)).block()).isTrue();
        assertThat(Mono.from(summaryRoute.getPredicate().apply(instanceSummary)).block()).isTrue();
    }

    @Test
    void guestFlightReadsAndRetryPostsPassSecurityWhileAccountAndUnknownPathsAreDenied() {
        assertSecurityAllows(HttpMethod.GET, "/api/v1/flight/UAL123/candidates");
        assertSecurityAllows(HttpMethod.GET, "/api/v1/flight/UAL123/instance/fa-123");
        assertSecurityAllows(HttpMethod.GET, "/api/v1/flight/UAL123/summary/fa-123");
        assertSecurityAllows(HttpMethod.POST, "/api/v1/flight/UAL123/instance/fa-123/summary/retry");
        assertSecurityAllows(HttpMethod.GET, "/actuator/prometheus");
        assertSecurityDenies(HttpMethod.GET, "/api/v1/auth/login");
        assertSecurityDenies(HttpMethod.GET, "/actuator/gateway/routes");
        assertSecurityDenies(HttpMethod.GET, "/api/v1/flight/UAL123/arbitrary");
        assertSecurityDenies(HttpMethod.POST, "/api/v1/flight/UAL123/arbitrary/future-route");
    }

    private void assertSecurityAllows(HttpMethod method, String path) {
        AtomicBoolean reachedHandler = new AtomicBoolean();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        securityFilterChain.filter(exchange, ignored -> {
            reachedHandler.set(true);
            return Mono.empty();
        }).block();
        assertThat(reachedHandler).as("Unauthenticated %s %s reaches the gateway", method, path).isTrue();
    }

    private void assertSecurityDenies(HttpMethod method, String path) {
        AtomicBoolean reachedHandler = new AtomicBoolean();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        securityFilterChain.filter(exchange, ignored -> {
            reachedHandler.set(true);
            return Mono.empty();
        }).block();
        assertThat(reachedHandler).as("%s %s is not forwarded", method, path).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isIn(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                org.springframework.http.HttpStatus.FORBIDDEN);
    }
}
