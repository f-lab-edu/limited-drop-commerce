package com.mist.commerce.domain.payment.application.listener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentCompletedEvent(
        Long orderId,
        Long paymentId,
        Long userId,
        BigDecimal amount,
        LocalDateTime occurredAt) {
}
