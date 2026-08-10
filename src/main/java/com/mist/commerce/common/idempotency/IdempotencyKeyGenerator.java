package com.mist.commerce.common.idempotency;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyGenerator {
    public String generate(IdempotencyRequest request) {
        return request.scope()
                + ":"
                + request.idempotencyKey();
    }
}

