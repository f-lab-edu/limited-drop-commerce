package com.mist.commerce.common.idempotency.model;

public record ClaimResult(ClaimStatus status, String resultPayload) {
}
