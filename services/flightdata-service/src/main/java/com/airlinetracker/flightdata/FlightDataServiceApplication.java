package com.airlinetracker.flightdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Flight Data Service Application
 * 
 * Responsibility (from ARCHITECTURE.md):
 * - Fetch flight data from FlightAware AeroAPI
 * - Implement Cache-Aside pattern with Redis (5 minute TTL)
 * - Publish flight-data-updated events to Kafka
 * - Register with Eureka service registry
 * 
 * Port: 8081 (as per ARCHITECTURE.md)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableCaching
public class FlightDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightDataServiceApplication.class, args);
    }
}

