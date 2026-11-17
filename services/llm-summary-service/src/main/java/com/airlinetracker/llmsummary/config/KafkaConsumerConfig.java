package com.airlinetracker.llmsummary.config;

import com.airlinetracker.llmsummary.dto.FlightData;
import com.airlinetracker.llmsummary.exception.OpenAIProviderException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

/**
 * Kafka Consumer Configuration for llm-summary-service.
 * Configures JSON deserialization for FlightData objects.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, FlightData> consumerFactory(
            KafkaProperties kafkaProperties, ObjectProvider<SslBundles> sslBundlesProvider) {
        Map<String, Object> config = flightDataConsumerProperties(
                kafkaProperties, sslBundlesProvider.getIfAvailable());
        
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(FlightData.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FlightData> kafkaListenerContainerFactory(
            ConsumerFactory<String, FlightData> consumerFactory, DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, FlightData> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(
            KafkaProperties kafkaProperties, ObjectProvider<SslBundles> sslBundlesProvider) {
        Map<String, Object> producerProperties = deadLetterProducerProperties(
                kafkaProperties, sslBundlesProvider.getIfAvailable());
        ProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(
                producerProperties, deadLetterKeySerializer(), deadLetterValueSerializer());
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            @Qualifier("deadLetterKafkaTemplate") KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        return recoverer;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, exponentialBackOff());
        handler.setBackOffFunction((record, exception) -> providerBackOff(record, exception));
        return handler;
    }

    @Bean
    public NewTopic flightDataEventsDeadLetterTopic(
            @Value("${kafka.topics.flight-data-events:flight-data-events}") String topic,
            @Value("${kafka.topics.partitions:1}") int partitions,
            @Value("${kafka.topics.replication-factor:1}") int replicationFactor) {
        return topicDefinition(topic + ".DLT", partitions, replicationFactor);
    }

    static JsonSerializer<Object> deadLetterValueSerializer() {
        return new JsonSerializer<>();
    }

    static Serializer<Object> deadLetterKeySerializer() {
        return (topic, key) -> key == null ? null : key.toString().getBytes(StandardCharsets.UTF_8);
    }

    static Map<String, Object> deadLetterProducerProperties(
            KafkaProperties kafkaProperties, SslBundles sslBundles) {
        return new HashMap<>(kafkaProperties.buildProducerProperties(sslBundles));
    }

    static Map<String, Object> flightDataConsumerProperties(
            KafkaProperties kafkaProperties, SslBundles sslBundles) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties(sslBundles));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FlightData.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return config;
    }

    static NewTopic topicDefinition(String name, int partitions, int replicationFactor) {
        if (partitions < 1 || replicationFactor < 1) {
            throw new IllegalArgumentException("Kafka topic partitions and replication factor must be positive");
        }
        return TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build();
    }

    static BackOff providerBackOff(ConsumerRecord<?, ?> record, Exception exception) {
        OpenAIProviderException providerFailure = findProviderFailure(exception);
        if (providerFailure == null) return null;

        int status = providerFailure.getStatusCode();
        if (status == 429 && providerFailure.getRetryAfter() != null
                && !providerFailure.getRetryAfter().isZero()) {
            return new FixedBackOff(providerFailure.getRetryAfter().toMillis(), 3L);
        }
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
            return new FixedBackOff(0L, 0L);
        }
        return exponentialBackOff();
    }

    private static OpenAIProviderException findProviderFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof OpenAIProviderException providerFailure) return providerFailure;
            if (cause.getCause() == cause) break;
        }
        return null;
    }

    static ExponentialBackOffWithMaxRetries exponentialBackOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8_000L);
        return backOff;
    }
}

