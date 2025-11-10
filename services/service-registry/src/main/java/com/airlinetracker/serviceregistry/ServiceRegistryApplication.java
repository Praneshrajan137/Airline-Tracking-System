package com.airlinetracker.serviceregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service Registry Application (Eureka Server)
 * 
 * Responsibility (from ARCHITECTURE.md):
 * - Service registration and discovery
 * - Maintains registry of all available service instances
 * - Provides health check endpoints
 * - Enables client-side load balancing
 * 
 * Port: 8761 (as per ARCHITECTURE.md)
 */
@SpringBootApplication
@EnableEurekaServer
public class ServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceRegistryApplication.class, args);
    }
}

