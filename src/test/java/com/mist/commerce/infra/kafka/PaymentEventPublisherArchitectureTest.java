package com.mist.commerce.infra.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.payment.event.PaymentCompletedEvent;
import com.mist.commerce.domain.payment.event.PaymentEventPublisher;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentEventPublisherArchitectureTest {

    @Test
    @DisplayName("TC-KAFKA-PUB-004: PaymentEventPublisher 포트는 Kafka 타입에 의존하지 않고 Kafka 어댑터가 포트를 구현한다")
    void paymentEventPublisherPortDoesNotExposeKafkaTypesAndKafkaAdapterImplementsPort() throws Exception {
        Method publishPaymentCompleted = PaymentEventPublisher.class.getDeclaredMethod(
                "publishPaymentCompleted",
                PaymentCompletedEvent.class);

        assertThat(PaymentEventPublisher.class.isInterface()).isTrue();
        assertThat(publishPaymentCompleted.getReturnType()).isEqualTo(void.class);
        assertThat(publishPaymentCompleted.getParameterTypes()).containsExactly(PaymentCompletedEvent.class);
        assertThat(Arrays.stream(PaymentEventPublisher.class.getDeclaredMethods()))
                .allSatisfy(this::assertMethodDoesNotExposeSpringKafkaType);
        assertThat(PaymentEventPublisher.class).isAssignableFrom(KafkaPaymentEventPublisher.class);
    }

    private void assertMethodDoesNotExposeSpringKafkaType(Method method) {
        assertThat(isSpringKafkaType(method.getReturnType())).isFalse();
        assertThat(Arrays.stream(method.getParameters())
                .map(Parameter::getType)
                .anyMatch(this::isSpringKafkaType)).isFalse();
    }

    private boolean isSpringKafkaType(Class<?> type) {
        Package typePackage = type.getPackage();
        return typePackage != null && typePackage.getName().startsWith("org.springframework.kafka");
    }
}
