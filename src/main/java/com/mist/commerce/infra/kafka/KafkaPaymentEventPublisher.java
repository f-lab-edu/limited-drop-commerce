package com.mist.commerce.infra.kafka;

import com.mist.commerce.domain.payment.event.PaymentCompletedEvent;
import com.mist.commerce.domain.payment.event.PaymentEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private final String topic;

    public KafkaPaymentEventPublisher(
            KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate,
            @Value("${payment.kafka.topic.completed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send(topic, String.valueOf(event.orderId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(
                                "Failed to publish PaymentCompletedEvent: orderId={}, paymentId={}",
                                event.orderId(),
                                event.paymentId(),
                                ex);
                    }
                });
    }
}
