package com.mist.commerce.domain.event.exception;

public enum EventExceptionMessage {
    EVENT_REGISTRATION_FORBIDDEN("이벤트를 등록할 권한이 없습니다."),
    EVENT_SCHEDULE_VALIDATION_ERROR("이벤트 일정이 유효하지 않습니다.");

    private final String message;

    EventExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
