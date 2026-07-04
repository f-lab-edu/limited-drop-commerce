package com.mist.commerce.domain.reservation.application.listener;

import com.mist.commerce.domain.reservation.dto.ReserveResult;

public record ReservationCreatedAfterCommitEvent(
        Long userId,
        String idempotencyKey,
        String fingerprint,
        ReserveResult result
) {
}
