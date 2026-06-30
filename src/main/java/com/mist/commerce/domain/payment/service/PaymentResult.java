package com.mist.commerce.domain.payment.service;

public record PaymentResult(
        Long paymentId,
        String paymentNo,
        String orderStatus,
        String paymentStatus) {
}
