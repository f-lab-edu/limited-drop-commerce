package com.mist.commerce.domain.reservation.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class OptionStockRedisRepositoryTest {

    private static final Long OPTION_STOCK_ID = 40L;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private OptionStockRedisRepository optionStockRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @AfterEach
    void cleanup() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-001: initialize 후 getRemaining은 초기 재고를 반환한다")
    void initialize_thenGetRemaining_returnsInitialQuantity() {
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, 100);

        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(100L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-002: 충분한 재고를 tryDecrease하면 차감 후 새 잔여 수량을 반환한다")
    void tryDecrease_whenEnoughStock_decreasesAndReturnsNewRemaining() {
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, 10);

        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 3);

        assertThat(remaining).isEqualTo(7L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(7L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-003: 재고가 부족하면 -1을 반환하고 수량을 변경하지 않는다")
    void tryDecrease_whenInsufficientStock_returnsMinusOneAndDoesNotChangeRemaining() {
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, 2);

        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 3);

        assertThat(remaining).isEqualTo(-1L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(2L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-004: 미초기화 키를 tryDecrease하면 -1을 반환한다")
    void tryDecrease_whenKeyDoesNotExist_returnsMinusOne() {
        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 1);

        assertThat(remaining).isEqualTo(-1L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isNull();
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-005: increase는 잔여 수량을 증가시킨다")
    void increase_addsQuantityToRemaining() {
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, 2);

        optionStockRedisRepository.increase(OPTION_STOCK_ID, 3);

        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(5L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-006: 200개 동시 차감 요청에서 100개만 성공하고 재고는 음수가 되지 않는다")
    void tryDecrease_concurrently_allowsOnlyAvailableQuantity() throws Exception {
        int initialQuantity = 100;
        int threadCount = 200;
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, initialQuantity);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger unexpectedNegativeCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                long result = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 1);
                if (result >= 0) {
                    successCount.incrementAndGet();
                } else if (result == -1L) {
                    failureCount.incrementAndGet();
                } else {
                    unexpectedNegativeCount.incrementAndGet();
                }
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(initialQuantity);
        assertThat(failureCount.get()).isEqualTo(threadCount - initialQuantity);
        assertThat(unexpectedNegativeCount.get()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isZero();
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-007: 미초기화 키는 fallbackAvailable로 지연 시딩한 뒤 차감한다")
    void tryDecreaseWithFallback_whenKeyDoesNotExist_seedsAndDecreases() {
        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 3, 10);

        assertThat(remaining).isEqualTo(7L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(7L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-008: 키가 이미 있으면 fallbackAvailable을 무시하고 기존 값 기준으로 차감한다")
    void tryDecreaseWithFallback_whenKeyExists_ignoresFallbackAndUsesExistingRemaining() {
        optionStockRedisRepository.initialize(OPTION_STOCK_ID, 5);

        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 2, 100);

        assertThat(remaining).isEqualTo(3L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(3L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-009: fallbackAvailable이 요청 수량보다 작으면 시딩 후 -1을 반환하고 차감하지 않는다")
    void tryDecreaseWithFallback_whenSeededStockIsInsufficient_seedsAndReturnsMinusOne() {
        long remaining = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 5, 2);

        assertThat(remaining).isEqualTo(-1L);
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isEqualTo(2L);
    }

    @Test
    @DisplayName("TC-REDIS-STOCK-010: 미초기화 키의 200개 동시 지연 시딩 차감 요청에서 100개만 성공한다")
    void tryDecreaseWithFallback_concurrentlySeedsOnceAndAllowsOnlyFallbackAvailableQuantity() throws Exception {
        int fallbackAvailable = 100;
        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger unexpectedNegativeCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                long result = optionStockRedisRepository.tryDecrease(OPTION_STOCK_ID, 1, fallbackAvailable);
                if (result >= 0) {
                    successCount.incrementAndGet();
                } else if (result == -1L) {
                    failureCount.incrementAndGet();
                } else {
                    unexpectedNegativeCount.incrementAndGet();
                }
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(fallbackAvailable);
        assertThat(failureCount.get()).isEqualTo(threadCount - fallbackAvailable);
        assertThat(unexpectedNegativeCount.get()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(OPTION_STOCK_ID)).isZero();
    }

    @SpringBootConfiguration
    @Import(OptionStockRedisRepository.class)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class TestConfig {
    }
}
