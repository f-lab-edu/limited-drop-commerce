package com.mist.commerce.domain.reservation.redis;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptionStockRedisRepository {

    private static final String KEY_PREFIX = "stock:option:";
    private static final DefaultRedisScript<Long> TRY_DECREASE_SCRIPT = tryDecreaseScript();
    private static final DefaultRedisScript<Long> TRY_DECREASE_WITH_FALLBACK_SCRIPT = tryDecreaseWithFallbackScript();

    private final StringRedisTemplate redisTemplate;

    public void initialize(Long optionStockId, int availableQuantity) {
        redisTemplate.opsForValue().set(key(optionStockId), String.valueOf(availableQuantity));
    }

    public long tryDecrease(Long optionStockId, int quantity) {
        Long result = redisTemplate.execute(TRY_DECREASE_SCRIPT, List.of(key(optionStockId)), String.valueOf(quantity));
        return result == null ? -1L : result;
    }

    public long tryDecrease(Long optionStockId, int quantity, int fallbackAvailable) {
        Long result = redisTemplate.execute(
                TRY_DECREASE_WITH_FALLBACK_SCRIPT,
                List.of(key(optionStockId)),
                String.valueOf(quantity),
                String.valueOf(fallbackAvailable)
        );
        return result == null ? -1L : result;
    }

    public void increase(Long optionStockId, int quantity) {
        redisTemplate.opsForValue().increment(key(optionStockId), quantity);
    }

    public Long getRemaining(Long optionStockId) {
        String value = redisTemplate.opsForValue().get(key(optionStockId));
        return value == null ? null : Long.parseLong(value);
    }

    private String key(Long optionStockId) {
        return KEY_PREFIX + optionStockId;
    }

    private static DefaultRedisScript<Long> tryDecreaseScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/lua/option-stock-try-decrease.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private static DefaultRedisScript<Long> tryDecreaseWithFallbackScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/lua/option-stock-try-decrease-fallback.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
