package com.mist.commerce.infra.redis;

public record ClaimResult(ClaimStatus status, String resultPayload) {
}
