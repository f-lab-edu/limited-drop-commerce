package com.mist.commerce.common.idempotency;

import lombok.Builder;

@Builder
public record IdempotencyRequest(
        Long userId,
        String scope,
        String idempotencyKey,
        String fingerprint
) {

    public String generate() {
        return scope + ":" + idempotencyKey;
    }
}
