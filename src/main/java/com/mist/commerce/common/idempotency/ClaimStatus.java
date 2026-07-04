package com.mist.commerce.common.idempotency;

public enum ClaimStatus {
    CLAIMED,
    IN_PROGRESS,
    COMPLETED,
    MISMATCH
}
