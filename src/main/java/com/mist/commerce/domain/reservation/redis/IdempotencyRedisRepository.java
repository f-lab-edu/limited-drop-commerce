package com.mist.commerce.domain.reservation.redis;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
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
    private static final DefaultRedisScript<String> CLAIM_SCRIPT = claimScript();
    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = completeScript();

    private final StringRedisTemplate redisTemplate;

    public ClaimResult claim(Long userId, String key, String fingerprint, Duration ttl) {
        String result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(key(userId, key)),
                fingerprint,
                String.valueOf(ttl.toMillis())
        );
        return toClaimResult(result);
    }

    public void complete(Long userId, String key, String expectedFingerprint, String resultPayload) {
        redisTemplate.execute(COMPLETE_SCRIPT, List.of(key(userId, key)), expectedFingerprint, resultPayload);
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

    private static DefaultRedisScript<String> claimScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/lua/idempotency-claim.lua"));
        script.setResultType(String.class);
        return script;
    }

    private static DefaultRedisScript<Long> completeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/lua/idempotency-complete.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
