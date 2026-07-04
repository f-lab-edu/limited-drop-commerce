package com.mist.commerce.domain.reservation.repository;

import java.time.Duration;

public interface ReservationExpiryStore {

    void markExpiry(Long orderId, Duration ttl);

    void clearExpiry(Long orderId);
}
