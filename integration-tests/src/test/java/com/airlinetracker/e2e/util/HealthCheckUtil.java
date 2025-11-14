package com.airlinetracker.e2e.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Utility class for health check operations
 * Phase 5: E2E Integration Testing
 */
@Slf4j
public class HealthCheckUtil {

    private static final RestTemplate restTemplate = new RestTemplate();
    private static final int MAX_RETRIES = 30;
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(2);

    /**
     * Wait for a service to be healthy by polling its health endpoint
     *
     * @param serviceName Service name for logging
     * @param healthUrl   Health check URL (e.g., http://localhost:8761/actuator/health)
     * @return true if service is healthy, false if max retries exceeded
     */
    public static boolean waitForHealthy(String serviceName, String healthUrl) {
        log.info("Waiting for {} to be healthy at {}", serviceName, healthUrl);

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> health = restTemplate.getForObject(healthUrl, Map.class);

                if (health != null && "UP".equals(health.get("status"))) {
                    log.info("{} is healthy (attempt {}/{})", serviceName, i + 1, MAX_RETRIES);
                    return true;
                }

                log.debug("{} health check returned: {} (attempt {}/{})",
                        serviceName, health, i + 1, MAX_RETRIES);

            } catch (RestClientException e) {
                log.debug("{} not ready yet: {} (attempt {}/{})",
                        serviceName, e.getMessage(), i + 1, MAX_RETRIES);
            }

            try {
                Thread.sleep(RETRY_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Health check interrupted for {}", serviceName);
                return false;
            }
        }

        log.error("{} did not become healthy after {} retries", serviceName, MAX_RETRIES);
        return false;
    }

    /**
     * Wait for all microservices to be healthy
     *
     * @param apiGatewayUrl API Gateway base URL
     * @return true if all services are healthy
     */
    public static boolean waitForAllServices(String apiGatewayUrl) {
        log.info("Starting health checks for all services...");

        boolean allHealthy = true;

        // Check in dependency order
        allHealthy &= waitForHealthy("Eureka", "http://localhost:8761/actuator/health");
        allHealthy &= waitForHealthy("API Gateway", apiGatewayUrl + "/actuator/health");
        allHealthy &= waitForHealthy("FlightData Service", "http://localhost:8081/actuator/health");
        allHealthy &= waitForHealthy("LLM Summary Service", "http://localhost:8082/actuator/health");

        if (allHealthy) {
            log.info("✅ All services are healthy and ready for testing");
        } else {
            log.error("❌ Some services failed health checks");
        }

        return allHealthy;
    }
}


