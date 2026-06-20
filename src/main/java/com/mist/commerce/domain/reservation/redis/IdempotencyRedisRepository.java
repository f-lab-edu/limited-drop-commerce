package com.mist.commerce.domain.reservation.redis;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyRedisRepository {

    private static final String KEY_PREFIX = "idem:";
    private static final String VALUE_SEPARATOR = "|";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String CLAIM_LUA = """
            local cur = redis.call('GET', KEYS[1])
            local fingerprint = ARGV[1]
            local ttlMillis = tonumber(ARGV[2])

            if not cur then
                redis.call('SET', KEYS[1], 'PENDING|' .. string.len(fingerprint) .. '|' .. fingerprint, 'PX', ttlMillis)
                return 'CLAIMED'
            end

            local first = string.find(cur, '|', 1, true)
            local second = nil
            if first then
                second = string.find(cur, '|', first + 1, true)
            end
            if not first or not second then
                return 'MISMATCH'
            end

            local storedStatus = string.sub(cur, 1, first - 1)
            local fingerprintLength = tonumber(string.sub(cur, first + 1, second - 1))
            if not fingerprintLength then
                return 'MISMATCH'
            end

            local fingerprintStart = second + 1
            local fingerprintEnd = fingerprintStart + fingerprintLength - 1
            local storedFingerprint = string.sub(cur, fingerprintStart, fingerprintEnd)
            if storedFingerprint ~= fingerprint then
                return 'MISMATCH'
            end

            if storedStatus == 'PENDING' then
                return 'IN_PROGRESS'
            end
            if storedStatus == 'DONE' then
                return 'COMPLETED|' .. string.sub(cur, fingerprintEnd + 1)
            end
            return 'MISMATCH'
            """;
    private static final String COMPLETE_LUA = """
            local cur = redis.call('GET', KEYS[1])
            if not cur then
                return 0
            end

            local first = string.find(cur, '|', 1, true)
            local second = nil
            if first then
                second = string.find(cur, '|', first + 1, true)
            end
            if not first or not second then
                return 0
            end

            local storedStatus = string.sub(cur, 1, first - 1)
            if storedStatus ~= 'PENDING' then
                return 0
            end

            local fingerprintLength = tonumber(string.sub(cur, first + 1, second - 1))
            if not fingerprintLength then
                return 0
            end

            local fingerprintStart = second + 1
            local fingerprintEnd = fingerprintStart + fingerprintLength - 1
            local storedFingerprint = string.sub(cur, fingerprintStart, fingerprintEnd)
            if storedFingerprint ~= ARGV[1] then
                return 0
            end

            redis.call(
                'SET',
                KEYS[1],
                'DONE|' .. fingerprintLength .. '|' .. storedFingerprint .. ARGV[2],
                'KEEPTTL'
            )
            return 1
            """;

    private final StringRedisTemplate redisTemplate;

    public ClaimResult claim(Long userId, String key, String fingerprint, Duration ttl) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(CLAIM_LUA, String.class);
        String result = redisTemplate.execute(
                script,
                List.of(key(userId, key)),
                fingerprint,
                String.valueOf(ttl.toMillis())
        );
        return toClaimResult(result);
    }

    public void complete(Long userId, String key, String expectedFingerprint, String resultPayload) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(COMPLETE_LUA, Long.class);
        redisTemplate.execute(script, List.of(key(userId, key)), expectedFingerprint, resultPayload);
    }

    public void release(Long userId, String key) {
        redisTemplate.delete(key(userId, key));
    }

    private ClaimResult toClaimResult(String result) {
        if (result == null) {
            return new ClaimResult(ClaimStatus.MISMATCH, null);
        }

        String[] parts = result.split("\\" + VALUE_SEPARATOR, 2);
        ClaimStatus status = ClaimStatus.valueOf(parts[0]);
        String resultPayload = status == ClaimStatus.COMPLETED && parts.length == 2 ? parts[1] : null;
        return new ClaimResult(status, resultPayload);
    }

    private String key(Long userId, String key) {
        return KEY_PREFIX + userId + ":" + key;
    }
}
