package com.mist.commerce.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.payment.event.PaymentCompletedEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class KafkaPaymentEventPublisherIntegrationTest {

    private static final String TOPIC = "payment.completed";
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long USER_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 6, 27, 12, 3);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withKraft();

    @Test
    @DisplayName("TC-KAFKA-PUB-002: 실제 Kafka 브로커에 발행한 결제 완료 이벤트를 역직렬화해 수신한다")
    void publishPaymentCompleted_sendsEventToKafkaAndConsumerReceivesDeserializedEvent() {
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = kafkaTemplate();
        KafkaPaymentEventPublisher publisher = new KafkaPaymentEventPublisher(kafkaTemplate, TOPIC);
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID, PAYMENT_ID, USER_ID, AMOUNT, OCCURRED_AT);

        try (KafkaConsumer<String, PaymentCompletedEvent> consumer = kafkaConsumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(100));

            publisher.publishPaymentCompleted(event);
            kafkaTemplate.flush();

            ConsumerRecord<String, PaymentCompletedEvent> record = pollSingleRecord(consumer);

            assertThat(record).isNotNull();
            assertThat(record.topic()).isEqualTo(TOPIC);
            assertThat(record.key()).isEqualTo(String.valueOf(ORDER_ID));
            assertThat(record.value().orderId()).isEqualTo(ORDER_ID);
            assertThat(record.value().paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(record.value().userId()).isEqualTo(USER_ID);
            assertThat(record.value().amount()).isEqualByComparingTo(AMOUNT);
            assertThat(record.value().occurredAt()).isEqualTo(OCCURRED_AT);
        } finally {
            kafkaTemplate.destroy();
        }
    }

    private KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private KafkaConsumer<String, PaymentCompletedEvent> kafkaConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-completed-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mist.commerce.domain.payment.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentCompletedEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, PaymentCompletedEvent> pollSingleRecord(
            KafkaConsumer<String, PaymentCompletedEvent> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, PaymentCompletedEvent> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                assertThat(records.count()).isEqualTo(1);
                return records.iterator().next();
            }
        }
        return null;
    }
}
