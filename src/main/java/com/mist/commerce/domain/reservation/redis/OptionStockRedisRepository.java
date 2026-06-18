package com.mist.commerce.domain.reservation.redis;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptionStockRedisRepository {

    private static final String KEY_PREFIX = "stock:option:";
    private static final String TRY_DECREASE_LUA = """
            local cur = redis.call('GET', KEYS[1])
            if not cur then return -1 end
            cur = tonumber(cur)
            local q = tonumber(ARGV[1])
            if cur >= q then return redis.call('DECRBY', KEYS[1], q) else return -1 end
            """;

    private final StringRedisTemplate redisTemplate;

    public void initialize(Long optionStockId, int availableQuantity) {
        redisTemplate.opsForValue().set(key(optionStockId), String.valueOf(availableQuantity));
    }

    public long tryDecrease(Long optionStockId, int quantity) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(TRY_DECREASE_LUA, Long.class);
        Long result = redisTemplate.execute(script, List.of(key(optionStockId)), String.valueOf(quantity));
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
}
