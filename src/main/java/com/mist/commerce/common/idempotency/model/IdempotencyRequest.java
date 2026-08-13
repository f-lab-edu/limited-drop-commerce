package com.mist.commerce.common.idempotency.model;

import java.time.Duration;
import lombok.Builder;

@Builder
public record IdempotencyRequest(
        Long userId,
        String scope,
        String idempotencyKey,
        String fingerprint,
        Duration ttl
) {

    public String generate() {
        return scope + ":" + idempotencyKey;
    }
}
