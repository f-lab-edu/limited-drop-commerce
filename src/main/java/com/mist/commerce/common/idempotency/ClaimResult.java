package com.mist.commerce.common.idempotency;

public record ClaimResult(ClaimStatus status, String resultPayload) {
}
