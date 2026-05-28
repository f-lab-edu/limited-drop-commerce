package com.mist.commerce.domain.user.exception;


public enum UserExceptionMessage {
    NOT_FOUND("사용자를 찾을 수 없습니다.");

    private final String message;

    UserExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
