package com.mist.commerce.domain.reservation.application.listener;

public record ReservationIdempotencyClaimedEvent(
        Long userId,
        String idempotencyKey,
        String fingerprint
) {
}
