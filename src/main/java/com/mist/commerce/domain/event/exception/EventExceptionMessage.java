package com.mist.commerce.domain.event.exception;

public enum EventExceptionMessage {
    DROP_EVENT_NOT_FOUND("드롭 이벤트를 찾을 수 없습니다."),
    DROP_EVENT_NOT_OPEN("드롭 이벤트가 오픈 상태가 아닙니다."),
    DROP_EVENT_CLOSED("드롭 이벤트가 종료되었습니다."),
    EVENT_ITEM_OPTION_NOT_FOUND("이벤트 상품 옵션을 찾을 수 없습니다."),
    EVENT_REGISTRATION_FORBIDDEN("이벤트를 등록할 권한이 없습니다."),
    EVENT_SCHEDULE_VALIDATION_ERROR("이벤트 일정이 유효하지 않습니다."),
    INSUFFICIENT_STOCK("판매 가능 재고가 부족합니다."),
    STOCK_EXHAUSTED("판매 가능 재고가 모두 소진되었습니다.");

    private final String message;

    EventExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
