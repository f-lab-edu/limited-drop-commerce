package com.mist.commerce.common.idempotency.web;

import com.mist.commerce.common.idempotency.model.IdempotencyContext;
import jakarta.servlet.http.HttpServletRequest;

public final class IdempotencyContextHolder {

    private static final String ATTRIBUTE = IdempotencyContext.class.getName();

    private IdempotencyContextHolder() {}

    public static void set(HttpServletRequest request, IdempotencyContext context) {
        request.setAttribute(ATTRIBUTE, context);
    }

    public static IdempotencyContext get(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);

        if (value == null) {
            return null;
        }

        if (!(value instanceof IdempotencyContext context)) {
            throw new IllegalStateException("Invalid idempotency context: " + value.getClass());
        }
        return context;
    }
}
