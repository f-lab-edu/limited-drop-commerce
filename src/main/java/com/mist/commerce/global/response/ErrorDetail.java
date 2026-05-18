package com.mist.commerce.global.response;

public record ErrorDetail(String field, Object value, String reason) {
    public static ErrorDetail of(String field, Object value, String reason) {
        return new ErrorDetail(field, value, reason);
    }
}
