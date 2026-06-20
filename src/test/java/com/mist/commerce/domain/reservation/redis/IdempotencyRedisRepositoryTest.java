package com.mist.commerce.domain.reservation.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
class IdempotencyRedisRepositoryTest {

    private static final Long USER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final String FINGERPRINT = "fp-body-a";
    private static final String OTHER_FINGERPRINT = "fp-body-b";
    private static final String RESULT_PAYLOAD = "{\"orderId\":100,\"status\":\"PENDING_PAYMENT\"}";
    private static final Duration TTL = Duration.ofMinutes(30);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private IdempotencyRedisRepository idempotencyRedisRepository;

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
    @DisplayName("TC-REDIS-IDEM-001: 미존재 키 claim은 CLAIMED를 반환하고 PENDING으로 저장한다")
    void claim_whenKeyDoesNotExist_createsPendingAndReturnsClaimed() {
        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("PENDING", FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-002: PENDING 동일 지문 재claim은 IN_PROGRESS를 반환한다")
    void claim_whenPendingWithSameFingerprint_returnsInProgress() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.IN_PROGRESS);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("PENDING", FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-003: DONE 동일 지문 재claim은 COMPLETED와 저장된 결과 스냅샷을 반환한다")
    void claim_whenDoneWithSameFingerprint_returnsCompletedWithResultPayload() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);
        idempotencyRedisRepository.complete(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, RESULT_PAYLOAD);

        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.COMPLETED);
        assertThat(result.resultPayload()).isEqualTo(RESULT_PAYLOAD);
        assertStoredValueContains("DONE", FINGERPRINT, RESULT_PAYLOAD);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-004: PENDING 다른 지문 claim은 MISMATCH를 반환하고 기존 값을 유지한다")
    void claim_whenPendingWithDifferentFingerprint_returnsMismatch() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, OTHER_FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.MISMATCH);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("PENDING", FINGERPRINT);
        assertStoredValueDoesNotContain(OTHER_FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-005: DONE 다른 지문 claim은 MISMATCH를 반환하고 결과 스냅샷을 반환하지 않는다")
    void claim_whenDoneWithDifferentFingerprint_returnsMismatchWithoutResultPayload() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);
        idempotencyRedisRepository.complete(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, RESULT_PAYLOAD);

        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, OTHER_FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.MISMATCH);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("DONE", FINGERPRINT, RESULT_PAYLOAD);
        assertStoredValueDoesNotContain(OTHER_FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-006: release 후 같은 키 claim은 다시 CLAIMED를 반환한다")
    void release_thenClaimAgain_returnsClaimed() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        idempotencyRedisRepository.release(USER_ID, IDEMPOTENCY_KEY);
        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.CLAIMED);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("PENDING", FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-007: claim 시 키에 양수 TTL이 설정된다")
    void claim_setsPositiveTtl() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        Long ttlSeconds = redisTemplate.getExpire(redisKey(), TimeUnit.SECONDS);

        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-008: 같은 키 동시 claim은 정확히 1개만 CLAIMED이고 나머지는 IN_PROGRESS다")
    void claim_concurrently_allowsOnlyOneClaimed() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger claimedCount = new AtomicInteger();
        AtomicInteger inProgressCount = new AtomicInteger();
        AtomicInteger mismatchCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);
                if (result.status() == ClaimStatus.CLAIMED) {
                    claimedCount.incrementAndGet();
                } else if (result.status() == ClaimStatus.IN_PROGRESS) {
                    inProgressCount.incrementAndGet();
                } else if (result.status() == ClaimStatus.MISMATCH) {
                    mismatchCount.incrementAndGet();
                } else {
                    unexpectedCount.incrementAndGet();
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

        assertThat(claimedCount.get()).isOne();
        assertThat(inProgressCount.get()).isEqualTo(threadCount - 1);
        assertThat(mismatchCount.get()).isZero();
        assertThat(unexpectedCount.get()).isZero();
        assertStoredValueContains("PENDING", FINGERPRINT);
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-009: complete는 DONE 전환 후에도 기존 TTL을 보존한다")
    void complete_preservesPositiveTtl() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        idempotencyRedisRepository.complete(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, RESULT_PAYLOAD);
        Long ttlSeconds = redisTemplate.getExpire(redisKey(), TimeUnit.SECONDS);

        assertStoredValueContains("DONE", FINGERPRINT, RESULT_PAYLOAD);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
    }

    @Test
    @DisplayName("TC-REDIS-IDEM-010: complete에 다른 fingerprint를 주면 DONE으로 전환하지 않는다")
    void complete_whenExpectedFingerprintDoesNotMatch_doesNotComplete() {
        idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        idempotencyRedisRepository.complete(USER_ID, IDEMPOTENCY_KEY, OTHER_FINGERPRINT, RESULT_PAYLOAD);
        ClaimResult result = idempotencyRedisRepository.claim(USER_ID, IDEMPOTENCY_KEY, FINGERPRINT, TTL);

        assertThat(result.status()).isEqualTo(ClaimStatus.IN_PROGRESS);
        assertThat(result.resultPayload()).isNull();
        assertStoredValueContains("PENDING", FINGERPRINT);
        assertStoredValueDoesNotContain(RESULT_PAYLOAD);
    }

    private void assertStoredValueContains(String... expectedValues) {
        String value = redisTemplate.opsForValue().get(redisKey());

        assertThat(value).isNotNull();
        assertThat(value).contains(expectedValues);
    }

    private void assertStoredValueDoesNotContain(String unexpectedValue) {
        String value = redisTemplate.opsForValue().get(redisKey());

        assertThat(value).isNotNull();
        assertThat(value).doesNotContain(unexpectedValue);
    }

    private String redisKey() {
        return "idem:" + USER_ID + ":" + IDEMPOTENCY_KEY;
    }

    @SpringBootConfiguration
    @Import(IdempotencyRedisRepository.class)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class TestConfig {
    }
}
