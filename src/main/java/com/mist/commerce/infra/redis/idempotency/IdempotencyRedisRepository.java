package com.mist.commerce.infra.redis.idempotency;

import com.mist.commerce.common.idempotency.model.ClaimResult;
import com.mist.commerce.common.idempotency.model.ClaimStatus;
import com.mist.commerce.common.idempotency.port.IdempotencyStore;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyRedisRepository implements IdempotencyStore {

    private static final String KEY_PREFIX = "idem:";
    private static final String VALUE_SEPARATOR = "|";

    private final IdempotencyRedisScripts redisScripts;
    private final StringRedisTemplate redisTemplate;

    @Override
    public ClaimResult claim(Long userId, String key, String fingerprint, Duration ttl) {
        String result = redisTemplate.execute(
                redisScripts.getClaimScript(),
                List.of(key(userId, key)),
                fingerprint,
                String.valueOf(ttl.toMillis())
        );
        return toClaimResult(result);
    }

    @Override
    public void complete(Long userId, String key, String expectedFingerprint, String resultPayload) {
        redisTemplate.execute(redisScripts.getCompleteScript(), List.of(key(userId, key)), expectedFingerprint, resultPayload);
    }

    @Override
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
