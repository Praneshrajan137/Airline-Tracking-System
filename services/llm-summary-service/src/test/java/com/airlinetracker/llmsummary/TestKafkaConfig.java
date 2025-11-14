package com.airlinetracker.llmsummary;

import com.airlinetracker.llmsummary.dto.FlightData;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.util.HashMap;
import java.util.Map;

/**
 * Test configuration for Kafka producer in integration tests.
 * Overrides the default producer configuration to use JsonSerializer for FlightData.
 */
@TestConfiguration
public class TestKafkaConfig {

    @Bean
    public ProducerFactory<String, FlightData> testProducerFactory(
            @Autowired EmbeddedKafkaBroker embeddedKafkaBroker) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, FlightData> testKafkaTemplate(
            ProducerFactory<String, FlightData> testProducerFactory) {
        return new KafkaTemplate<>(testProducerFactory);
    }
}


