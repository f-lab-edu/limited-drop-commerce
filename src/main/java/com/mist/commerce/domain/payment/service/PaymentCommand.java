package com.mist.commerce.domain.payment.service;

import java.math.BigDecimal;

public record PaymentCommand(
        Long userId,
        Long orderId,
        String paymentKey,
        BigDecimal amount,
        String idempotencyKey) {
}
