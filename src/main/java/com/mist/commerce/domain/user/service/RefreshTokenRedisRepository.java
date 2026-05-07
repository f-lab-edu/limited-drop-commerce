package com.mist.commerce.domain.user.service;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String sessionId, String tokenValue, long ttlSeconds) {
        redisTemplate.opsForValue().set(key(sessionId), tokenValue, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<String> find(String sessionId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(sessionId)));
    }

    public void delete(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
