package com.mist.commerce.domain.reservation.redis;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationExpiryRedisRepository {

    private static final String KEY_PREFIX = "reservation:expiry:";

    private final StringRedisTemplate redisTemplate;

    public void markExpiry(Long orderId, Duration ttl) {
        redisTemplate.opsForValue().set(key(orderId), String.valueOf(orderId), ttl);
    }

    public void clearExpiry(Long orderId) {
        redisTemplate.delete(key(orderId));
    }

    public static String orderIdFromKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            return null;
        }

        String orderId = key.substring(KEY_PREFIX.length());
        return orderId.isEmpty() ? null : orderId;
    }

    private String key(Long orderId) {
        return KEY_PREFIX + orderId;
    }
}
