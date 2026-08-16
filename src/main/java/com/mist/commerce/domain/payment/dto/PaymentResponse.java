package com.mist.commerce.domain.payment.dto;

public record PaymentResponse(
        Long paymentId,
        String paymentNo,
        String orderStatus,
        String paymentStatus) {

    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(
                result.paymentId(),
                result.paymentNo(),
                result.orderStatus(),
                result.paymentStatus());
    }
}
