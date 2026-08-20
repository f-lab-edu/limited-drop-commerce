package com.mist.commerce.domain.payment.dto;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record PaymentCommand(
        Long userId,
        Long orderId,
        String paymentKey,
        BigDecimal amount,
        String idempotencyKey) {
}
