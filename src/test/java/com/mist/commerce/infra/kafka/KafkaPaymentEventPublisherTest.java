package com.mist.commerce.infra.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.payment.event.PaymentCompletedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaPaymentEventPublisherTest {

    private static final String TOPIC = "payment.completed";
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long USER_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 6, 27, 12, 3);

    @Test
    @DisplayName("TC-KAFKA-PUB-001: Kafka 어댑터는 설정 topic과 orderId key로 결제 완료 이벤트를 발행한다")
    void publishPaymentCompleted_sendsEventToInjectedTopicWithOrderIdKey() {
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPaymentEventPublisher publisher = new KafkaPaymentEventPublisher(kafkaTemplate, TOPIC);
        PaymentCompletedEvent event = paymentCompletedEvent();
        when(kafkaTemplate.send(TOPIC, String.valueOf(ORDER_ID), event))
                .thenReturn(CompletableFuture.<SendResult<String, PaymentCompletedEvent>>completedFuture(null));
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PaymentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);

        publisher.publishPaymentCompleted(event);

        verify(kafkaTemplate, times(1)).send(topicCaptor.capture(), keyCaptor.capture(), eventCaptor.capture());
        verifyNoMoreInteractions(kafkaTemplate);
        assertThat(topicCaptor.getValue()).isEqualTo(TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(eventCaptor.getValue()).isSameAs(event);
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(ORDER_ID);
        assertThat(eventCaptor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(eventCaptor.getValue().amount()).isEqualByComparingTo(AMOUNT);
        assertThat(eventCaptor.getValue().occurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("TC-KAFKA-PUB-005: Kafka 비동기 발행 실패는 호출자에게 예외로 전파하지 않는다")
    void publishPaymentCompleted_whenKafkaSendFailsAsynchronously_doesNotThrowToCaller() {
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        KafkaPaymentEventPublisher publisher = new KafkaPaymentEventPublisher(kafkaTemplate, TOPIC);
        PaymentCompletedEvent event = paymentCompletedEvent();
        when(kafkaTemplate.send(TOPIC, String.valueOf(ORDER_ID), event))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatCode(() -> publisher.publishPaymentCompleted(event))
                .doesNotThrowAnyException();

        verify(kafkaTemplate, times(1)).send(TOPIC, String.valueOf(ORDER_ID), event);
        verifyNoMoreInteractions(kafkaTemplate);
    }

    private PaymentCompletedEvent paymentCompletedEvent() {
        return new PaymentCompletedEvent(ORDER_ID, PAYMENT_ID, USER_ID, AMOUNT, OCCURRED_AT);
    }
}
