package com.airlinetracker.llmsummary.config;

import com.airlinetracker.llmsummary.exception.OpenAIProviderException;
import com.airlinetracker.llmsummary.dto.FlightData;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConsumerConfigTest {
    private final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("flight-data-events", 3, 0L, "UAL123-instance", "{}");

    @Test
    void createsAnErrorHandlerWithDeadLetterPublishing() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        DeadLetterPublishingRecoverer recoverer = config.deadLetterPublishingRecoverer(
                mock(KafkaTemplate.class));
        DefaultErrorHandler handler = config.kafkaErrorHandler(recoverer);

        assertThat(handler).isNotNull();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void routesDeadLetterToMatchingTopicAndSourcePartition() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.completedFuture(new SendResult<>(
                        new ProducerRecord<>("flight-data-events.DLT", 3, "UAL123-instance", "{}"),
                        mock(RecordMetadata.class))));
        DeadLetterPublishingRecoverer recoverer = new KafkaConsumerConfig()
                .deadLetterPublishingRecoverer(template);

        recoverer.accept(record, new RuntimeException("permanent provider error"));

        verify(template).send(org.mockito.ArgumentMatchers.<ProducerRecord<Object, Object>>argThat(sent ->
                sent.topic().equals("flight-data-events.DLT")
                        && sent.partition() == 3
                        && sent.key().equals("UAL123-instance")
                        && sent.value().equals("{}")));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void surfacesDeadLetterSendFailureToTheRecoveryHandler() {
        KafkaTemplate<Object, Object> template = mock(KafkaTemplate.class);
        when(template.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
        DeadLetterPublishingRecoverer recoverer = new KafkaConsumerConfig()
                .deadLetterPublishingRecoverer(template);

        assertThatThrownBy(() -> recoverer.accept(record, new RuntimeException("permanent provider error")))
                .isInstanceOf(KafkaException.class);
    }

    @Test
    void deadLetterSerializerEncodesFlightDataAsJson() throws Exception {
        FlightData flight = FlightData.builder()
                .faFlightId("UAL123-20261117-1")
                .ident("UAL123")
                .status("Scheduled")
                .build();

        byte[] payload = KafkaConsumerConfig.deadLetterValueSerializer()
                .serialize("flight-data-events.DLT", flight);

        assertThat(new ObjectMapper().readTree(payload).path("fa_flight_id").asText())
                .isEqualTo("UAL123-20261117-1");
    }

    @Test
    void inheritsBootstrapAndSecurityPropertiesForDeadLetterProducer() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("broker.example:9093"));
        properties.getProperties().put("security.protocol", "SASL_SSL");
        properties.getProperties().put("sasl.mechanism", "PLAIN");

        var producerProperties = KafkaConsumerConfig.deadLetterProducerProperties(properties);

        assertThat(producerProperties).containsEntry("bootstrap.servers", List.of("broker.example:9093"));
        assertThat(producerProperties).containsEntry("security.protocol", "SASL_SSL");
        assertThat(producerProperties).containsEntry("sasl.mechanism", "PLAIN");
    }

    @Test
    void inheritsBootstrapAndSecurityPropertiesForFlightDataConsumer() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("broker.example:9093"));
        properties.getConsumer().setGroupId("flight-data-consumer");
        properties.getProperties().put("security.protocol", "SASL_SSL");
        properties.getProperties().put("sasl.mechanism", "PLAIN");

        var consumerProperties = KafkaConsumerConfig.flightDataConsumerProperties(properties);

        assertThat(consumerProperties).containsEntry("bootstrap.servers", List.of("broker.example:9093"));
        assertThat(consumerProperties).containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "flight-data-consumer");
        assertThat(consumerProperties).containsEntry("security.protocol", "SASL_SSL");
        assertThat(consumerProperties).containsEntry("sasl.mechanism", "PLAIN");
        assertThat(consumerProperties).containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        assertThat(consumerProperties).containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    }

    @Test
    void provisionsDeadLetterTopicWithConfiguredPartitionsAndReplication() {
        NewTopic deadLetter = KafkaConsumerConfig.topicDefinition("flight-data-events.DLT", 4, 3);

        assertThat(deadLetter.name()).isEqualTo("flight-data-events.DLT");
        assertThat(deadLetter.numPartitions()).isEqualTo(4);
        assertThat(deadLetter.replicationFactor()).isEqualTo((short) 3);
    }

    @Test
    void rejectsDeadLetterTopicWithInvalidPartitionCounts() {
        assertThatThrownBy(() -> KafkaConsumerConfig.topicDefinition("flight-data-events.DLT", 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka topic partitions and replication factor must be positive");
    }

    @Test
    void usesProviderRetryAfterAndThenStopsAfterBoundedAttempts() {
        BackOff backOff = KafkaConsumerConfig.providerBackOff(record,
                new RuntimeException("wrapped", new OpenAIProviderException(429, Duration.ofSeconds(2))));
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void doesNotRetryPermanentProviderClientErrors() {
        BackOff backOff = KafkaConsumerConfig.providerBackOff(record,
                new OpenAIProviderException(401, null));

        assertThat(backOff.start().nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void retriesTransientFailuresWithBoundedExponentialDelays() {
        ExponentialBackOffWithMaxRetries backOff = KafkaConsumerConfig.exponentialBackOff();
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(2_000L);
        assertThat(execution.nextBackOff()).isEqualTo(4_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
