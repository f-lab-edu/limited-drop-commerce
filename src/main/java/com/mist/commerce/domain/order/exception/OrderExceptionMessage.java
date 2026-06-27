package com.mist.commerce.domain.order.exception;

public enum OrderExceptionMessage {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다."),
    ORDER_FORBIDDEN("주문에 접근할 수 없습니다."),
    ORDER_ALREADY_CANCELLED("이미 취소된 주문입니다."),
    ORDER_CANNOT_CANCEL("취소할 수 없는 주문입니다."),
    ORDER_CANNOT_PAY("결제할 수 없는 주문입니다."),
    ORDER_CANCEL_TEMPORARILY_UNAVAILABLE("현재 주문 취소를 처리할 수 없습니다.");

    private final String message;

    OrderExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
