package com.mist.commerce.infra.redis.reservation;

import com.mist.commerce.domain.reservation.repository.ReservationExpiryStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisReservationExpiryStore implements ReservationExpiryStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void markExpiry(Long orderId, Duration ttl) {
        ReservationExpiryKey expiryKey = ReservationExpiryKey.of(orderId);
        redisTemplate.opsForValue().set(expiryKey.rawkey(), String.valueOf(orderId), ttl);
    }

    @Override
    public void clearExpiry(Long orderId) {
        ReservationExpiryKey expiryKey = ReservationExpiryKey.of(orderId);
        redisTemplate.delete(expiryKey.rawkey());
    }
}
