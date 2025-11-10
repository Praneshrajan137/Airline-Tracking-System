package com.airlinetracker.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application
 * 
 * Responsibility (from ARCHITECTURE.md):
 * - Single entry point for all client requests
 * - Routes requests to appropriate backend services
 * - Implements rate limiting, CORS, and request/response transformation
 * - Discovers services via Eureka
 * 
 * Port: 8080 (as per ARCHITECTURE.md)
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

