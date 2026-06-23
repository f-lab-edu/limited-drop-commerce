package com.mist.commerce.domain.reservation.redis;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.CommerceApplication;
import com.mist.commerce.domain.reservation.service.ExpiryRecoveryService;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = CommerceApplication.class)
@Testcontainers
class ReservationExpiryEventListenerTest extends MySqlContainerTestSupport {

    private static final Long ORDER_ID = 777L;
    private static final Duration MARKER_TTL = Duration.ofSeconds(2);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ExpiryRecoveryService expiryRecoveryService;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("TC-EXPIRY-EVENT-001: 만료 마커 키 TTL 만료 이벤트는 주문 복구를 호출한다")
    void expiredReservationMarkerEvent_recoversOrder() {
        redisTemplate.opsForValue().set("reservation:expiry:777", "777", MARKER_TTL);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(expiryRecoveryService).recover(ORDER_ID));
    }

    @Test
    @DisplayName("TC-EXPIRY-EVENT-002: 비대상 키 만료 이벤트는 복구를 호출하지 않는다")
    void expiredNonReservationMarkerEvent_doesNotRecoverOrder() throws InterruptedException {
        redisTemplate.opsForValue().set("stock:option:1", "1", MARKER_TTL);

        Thread.sleep(Duration.ofSeconds(4));

        verify(expiryRecoveryService, never()).recover(anyLong());
    }
}
