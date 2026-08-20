package com.mist.commerce.common.idempotency.model;

public record IdempotencyContext(Long userId, String redisKey, String fingerprint) {
}
