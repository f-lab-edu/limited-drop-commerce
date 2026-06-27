package com.mist.commerce.domain.payment.gateway;

import java.math.BigDecimal;

public record PaymentApprovalCommand(
        String paymentKey,
        String orderId,
        BigDecimal amount
) {
}
