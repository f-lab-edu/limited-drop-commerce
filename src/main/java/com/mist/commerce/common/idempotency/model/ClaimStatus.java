package com.mist.commerce.common.idempotency.model;

public enum ClaimStatus {
    CLAIMED,
    IN_PROGRESS,
    COMPLETED,
    MISMATCH
}
