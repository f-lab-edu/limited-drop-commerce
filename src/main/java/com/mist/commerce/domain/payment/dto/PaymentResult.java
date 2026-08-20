package com.mist.commerce.domain.payment.dto;

public record PaymentResult(
        Long paymentId,
        String paymentNo,
        String orderStatus,
        String paymentStatus) {
}
