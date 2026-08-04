package com.mist.commerce.common.idempotency;

import jakarta.servlet.http.HttpServletRequest;

public record IdempotencyContext(Long userId, String redisKey, String fingerprint) {
    private static final String ATTRIBUTE = IdempotencyContext.class.getName();

    public void writeTo(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, this);
    }

    public static IdempotencyContext from(HttpServletRequest request) {
        return (IdempotencyContext) request.getAttribute(ATTRIBUTE);
    }
}
