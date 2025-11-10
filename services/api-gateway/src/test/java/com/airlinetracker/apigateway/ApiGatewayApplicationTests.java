package com.airlinetracker.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for API Gateway
 * 
 * Test Requirements (from ARCHITECTURE.md):
 * - API Gateway must run on port 8080
 * - Must route /api/v1/flight/** to flightdata-service
 * - Must route /api/v1/flight/** /summary to llm-summary-service
 * - Must use load balancing (lb://)
 * - Must register with Eureka
 */
@SpringBootTest
@TestPropertySource(properties = {
    "eureka.client.enabled=false" // Disable Eureka for tests
})
class ApiGatewayApplicationTests {

    @Autowired(required = false)
    private RouteLocator routeLocator;

    /**
     * Test: Spring Boot context should load successfully
     */
    @Test
    void contextLoads() {
        assertThat(routeLocator).isNotNull();
    }

    /**
     * Test: Gateway should have route to flightdata-service
     * 
     * Requirement: api-gateway must route /api/v1/flight/** to FLIGHTDATA-SERVICE
     */
    @Test
    void shouldHaveFlightDataServiceRoute() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        
        var flightDataRoute = routes.stream()
            .filter(route -> route.getId().equals("flightdata-service-route"))
            .findFirst();
        
        assertThat(flightDataRoute).isPresent();
        assertThat(flightDataRoute.get().getUri().toString())
            .contains("lb://FLIGHTDATA-SERVICE");
    }

    /**
     * Test: Gateway should have route to llm-summary-service
     * 
     * Requirement: api-gateway must route summary requests to LLM-SUMMARY-SERVICE
     */
    @Test
    void shouldHaveLlmSummaryServiceRoute() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        
        var summaryRoute = routes.stream()
            .filter(route -> route.getId().equals("llm-summary-service-route"))
            .findFirst();
        
        assertThat(summaryRoute).isPresent();
        assertThat(summaryRoute.get().getUri().toString())
            .contains("lb://LLM-SUMMARY-SERVICE");
    }

    /**
     * Test: Routes should use load balancing
     * 
     * Verifies that routes use lb:// scheme for client-side load balancing
     */
    @Test
    void shouldUseLoadBalancing() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        
        // All routes should use lb:// scheme
        routes.forEach(route -> {
            String uriScheme = route.getUri().getScheme();
            if (!route.getId().contains("actuator")) {
                assertThat(uriScheme)
                    .as("Route %s should use load balancing", route.getId())
                    .isEqualTo("lb");
            }
        });
    }

    /**
     * Test: Gateway should have at least 2 service routes
     * 
     * Verifies that both flightdata-service and llm-summary-service routes are configured
     */
    @Test
    void shouldHaveTwoServiceRoutes() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        
        var serviceRoutes = routes.stream()
            .filter(route -> route.getId().contains("service-route"))
            .count();
        
        assertThat(serviceRoutes)
            .as("Should have 2 service routes (flightdata and llm-summary)")
            .isEqualTo(2);
    }
}

