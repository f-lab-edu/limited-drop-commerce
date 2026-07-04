package com.mist.commerce.infra.redis.idempotency;

import com.mist.commerce.infra.redis.RedisScriptLoader;
import lombok.Getter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Getter
@Component
public class IdempotencyRedisScripts {
    private static final String CLAIM_IDEMPOTENCY_KEY_SCRIPT_PATH = "redis/lua/idempotency-claim.lua";
    private static final String COMPLETE_IDEMPOTENCY_KEY_SCRIPT_PATH = "redis/lua/idempotency-complete.lua";

    private final RedisScript<String> claimScript;
    private final RedisScript<Long> completeScript;

    public IdempotencyRedisScripts(RedisScriptLoader redisScriptLoader) {
        this.claimScript = redisScriptLoader.loadStringScript(CLAIM_IDEMPOTENCY_KEY_SCRIPT_PATH);
        this.completeScript = redisScriptLoader.loadLongScript(COMPLETE_IDEMPOTENCY_KEY_SCRIPT_PATH);
    }
}
