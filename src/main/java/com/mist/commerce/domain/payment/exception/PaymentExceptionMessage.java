package com.mist.commerce.domain.payment.exception;

public enum PaymentExceptionMessage {
    PAYMENT_FAILED("결제 승인에 실패했습니다."),
    PAYMENT_AMOUNT_MISMATCH("결제 금액이 주문 금액과 일치하지 않습니다.");

    private final String message;

    PaymentExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
