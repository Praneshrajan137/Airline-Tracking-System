package com.airlinetracker.serviceregistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Service Registry (Eureka Server)
 * 
 * Test Requirements (from ARCHITECTURE.md):
 * - Service must start on port 8761
 * - Eureka server must respond to /eureka/apps
 * - Service must be healthy and discoverable
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ServiceRegistryApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Test: Spring Boot context should load successfully
     * 
     * Verifies that the application context starts without errors
     */
    @Test
    void contextLoads() {
        // If context loads, this test passes
        assertThat(port).isEqualTo(8761);
    }

    /**
     * Test: Eureka server should be running on port 8761
     * 
     * Requirement: service-registry must start on port 8761 (ARCHITECTURE.md)
     */
    @Test
    void shouldStartOnPort8761() {
        assertThat(port).isEqualTo(8761);
    }

    /**
     * Test: /eureka/apps endpoint should return 200 OK
     * 
     * Requirement: Eureka server must respond to /eureka/apps
     * This endpoint lists all registered applications
     */
    @Test
    void shouldRespondToEurekaAppsEndpoint() {
        String url = "http://localhost:" + port + "/eureka/apps";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    /**
     * Test: Eureka dashboard should be accessible
     * 
     * Verifies the Eureka web UI is available
     */
    @Test
    void shouldServeDashboard() {
        String url = "http://localhost:" + port + "/";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Test: Actuator health endpoint should return UP
     * 
     * Verifies service health checks are working
     */
    @Test
    void shouldBeHealthy() {
        String url = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}

