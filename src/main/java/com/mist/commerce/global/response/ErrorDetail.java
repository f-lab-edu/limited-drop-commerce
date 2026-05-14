package com.mist.commerce.global.response;

public record ErrorDetail(String field, Object value, String reason) {
}
