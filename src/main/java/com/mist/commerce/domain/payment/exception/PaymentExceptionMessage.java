package com.mist.commerce.domain.payment.exception;

public enum PaymentExceptionMessage {
    PAYMENT_FAILED("결제 승인에 실패했습니다.");

    private final String message;

    PaymentExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
