package com.mist.commerce.domain.reservation.exception;

public enum ReservationExceptionMessage {
    INVALID_RESERVATION_QUANTITY("선점 수량이 유효하지 않습니다."),
    PURCHASE_LIMIT_EXCEEDED("구매 제한 수량을 초과했습니다."),
    ACTIVE_RESERVATION_ALREADY_EXISTS("활성 선점이 이미 존재합니다."),
    RESERVATION_TEMPORARILY_UNAVAILABLE("현재 재고 선점을 처리할 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED("이미 사용된 멱등성 키입니다."),
    RESERVATION_IN_PROGRESS("동일한 예약 요청이 처리 중입니다.");

    private final String message;

    ReservationExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
