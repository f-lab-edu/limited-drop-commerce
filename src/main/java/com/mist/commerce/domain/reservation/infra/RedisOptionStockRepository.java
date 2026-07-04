package com.mist.commerce.domain.reservation.infra;

import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisOptionStockRepository implements OptionStockStore {

    private static final String KEY_PREFIX = "stock:option:";
    private final OptionStockRedisScripts optionStockRedisScripts;
    private final StringRedisTemplate redisTemplate;


    @Override
    public void initialize(Long optionStockId, int availableQuantity) {
        redisTemplate.opsForValue().set(key(optionStockId), String.valueOf(availableQuantity));
    }

    @Override
    public long tryDecrease(Long optionStockId, int quantity) {
        Long result = redisTemplate.execute(
                optionStockRedisScripts.getDecreaseScript(),
                List.of(key(optionStockId)),
                String.valueOf(quantity)
        );
        return result == null ? -1L : result;
    }

    @Override
    public long tryDecrease(Long optionStockId, int quantity, int fallbackAvailable) {
        Long result = redisTemplate.execute(
                optionStockRedisScripts.getTryDecreaseWithFallbackScript(),
                List.of(key(optionStockId)),
                String.valueOf(quantity),
                String.valueOf(fallbackAvailable)
        );
        return result == null ? -1L : result;
    }

    @Override
    public void increase(Long optionStockId, int quantity) {
        redisTemplate.opsForValue().increment(key(optionStockId), quantity);
    }

    @Override
    public Long getRemaining(Long optionStockId) {
        String value = redisTemplate.opsForValue().get(key(optionStockId));
        return value == null ? null : Long.parseLong(value);
    }

    private String key(Long optionStockId) {
        return KEY_PREFIX + optionStockId;
    }
}
