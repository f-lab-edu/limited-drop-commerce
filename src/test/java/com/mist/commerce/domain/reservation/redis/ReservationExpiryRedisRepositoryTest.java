package com.mist.commerce.domain.reservation.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
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
class ReservationExpiryRedisRepositoryTest {

    private static final Long ORDER_ID = 42L;
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final long TTL_SECONDS = 1800L;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private ReservationExpiryRedisRepository reservationExpiryRedisRepository;

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
    @DisplayName("TC-EXPIRY-MARKER-001: markExpiry는 주문 만료 마커 키를 TTL과 함께 저장한다")
    void markExpiry_setsKeyValueAndTtl() {
        reservationExpiryRedisRepository.markExpiry(ORDER_ID, TTL);

        String key = expiryKey(ORDER_ID);
        String value = redisTemplate.opsForValue().get(key);
        Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

        assertThat(redisTemplate.hasKey(key)).isTrue();
        assertThat(value).isEqualTo("42");
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(TTL_SECONDS);
        assertThat(ttlSeconds).isGreaterThan(TTL_SECONDS - 10);
    }

    @Test
    @DisplayName("TC-EXPIRY-MARKER-002: clearExpiry는 주문 만료 마커 키를 삭제한다")
    void clearExpiry_deletesExpiryMarkerKey() {
        String key = expiryKey(ORDER_ID);
        redisTemplate.opsForValue().set(key, ORDER_ID.toString(), TTL);

        reservationExpiryRedisRepository.clearExpiry(ORDER_ID);

        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("TC-EXPIRY-MARKER-003: orderIdFromKey는 만료 마커 키에서 orderId 문자열을 파싱한다")
    void orderIdFromKey_whenExpiryMarkerKey_returnsOrderId() {
        String orderId = ReservationExpiryRedisRepository.orderIdFromKey("reservation:expiry:42");

        assertThat(orderId).isEqualTo("42");
    }

    @Test
    @DisplayName("TC-EXPIRY-MARKER-004: orderIdFromKey는 비대상 키이면 null을 반환한다")
    void orderIdFromKey_whenNotExpiryMarkerKey_returnsNull() {
        assertThat(ReservationExpiryRedisRepository.orderIdFromKey("stock:option:42")).isNull();
        assertThat(ReservationExpiryRedisRepository.orderIdFromKey("reservation:expiry:")).isNull();
    }

    private String expiryKey(Long orderId) {
        return "reservation:expiry:" + orderId;
    }

    @SpringBootConfiguration
    @Import(ReservationExpiryRedisRepository.class)
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class TestConfig {
    }
}
