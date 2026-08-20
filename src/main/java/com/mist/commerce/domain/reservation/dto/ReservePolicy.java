package com.mist.commerce.domain.reservation.dto;

import java.time.Duration;

public final class ReservePolicy {
    public static final Duration PAYMENT_TTL = Duration.ofMinutes(10);

    private ReservePolicy() {
    }
}
