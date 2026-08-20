package com.mist.commerce.infra.redis.reservation;

import com.mist.commerce.infra.redis.RedisScriptLoader;
import lombok.Getter;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Getter
@Component
public class OptionStockRedisScripts {
    private static final String TRY_DECREASE_SCRIPT_PATH = "redis/lua/option-stock-try-decrease.lua";
    private static final String TRY_DECREASE_WITH_FALLBACK_SCRIPT_PATH = "redis/lua/option-stock-try-decrease-fallback.lua";

    private final RedisScript<Long> decreaseScript;
    private final RedisScript<Long> tryDecreaseWithFallbackScript;

    public OptionStockRedisScripts(RedisScriptLoader redisScriptLoader) {
        this.decreaseScript = redisScriptLoader.loadLongScript(TRY_DECREASE_SCRIPT_PATH);
        this.tryDecreaseWithFallbackScript = redisScriptLoader.loadLongScript(TRY_DECREASE_WITH_FALLBACK_SCRIPT_PATH);
    }
}
