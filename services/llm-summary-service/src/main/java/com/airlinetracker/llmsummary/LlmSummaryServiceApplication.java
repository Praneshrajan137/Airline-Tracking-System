package com.airlinetracker.llmsummary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Main application class for LLM Summary Service.
 * 
 * Responsibilities:
 * - Consume flight-data-events from Kafka (PRD.md Section 3.2)
 * - Generate AI summaries using OpenAI API
 * - Store summaries in PostgreSQL
 * - Serve summaries via REST API
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class LlmSummaryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmSummaryServiceApplication.class, args);
    }
}

