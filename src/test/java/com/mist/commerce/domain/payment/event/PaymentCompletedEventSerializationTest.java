package com.mist.commerce.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCompletedEventSerializationTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long USER_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 6, 27, 12, 3);

    @Test
    @DisplayName("TC-KAFKA-PUB-003: PaymentCompletedEvent JSON 직렬화 계약은 필드 손실 없이 라운드트립된다")
    void paymentCompletedEvent_roundTripsThroughJsonWithoutFieldLoss() throws Exception {
        ObjectMapper objectMapper = objectMapper();
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID, PAYMENT_ID, USER_ID, AMOUNT, OCCURRED_AT);

        String json = objectMapper.writeValueAsString(event);
        PaymentCompletedEvent restored = objectMapper.readValue(json, PaymentCompletedEvent.class);
        JsonNode jsonNode = objectMapper.readTree(json);

        assertThat(jsonNode.has("orderId")).isTrue();
        assertThat(jsonNode.has("paymentId")).isTrue();
        assertThat(jsonNode.has("userId")).isTrue();
        assertThat(jsonNode.has("amount")).isTrue();
        assertThat(jsonNode.has("occurredAt")).isTrue();
        assertThat(jsonNode.size()).isEqualTo(5);
        assertThat(restored.orderId()).isEqualTo(ORDER_ID);
        assertThat(restored.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(restored.userId()).isEqualTo(USER_ID);
        assertThat(restored.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(restored.occurredAt()).isEqualTo(OCCURRED_AT);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
