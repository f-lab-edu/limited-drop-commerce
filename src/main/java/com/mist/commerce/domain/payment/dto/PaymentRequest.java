package com.mist.commerce.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull Long orderId,
        @NotBlank String paymentKey,
        String paymentMethod,
        @NotNull BigDecimal amount) {
}
