package com.mist.commerce.common.idempotency.port;

import com.mist.commerce.common.idempotency.model.ClaimResult;
import java.time.Duration;

public interface IdempotencyStore {

    ClaimResult claim(Long userId, String key, String fingerprint, Duration ttl);
    void complete(Long userId, String key, String expectedFingerprint, String resultPayload);
    void release(Long userId, String key);

}
