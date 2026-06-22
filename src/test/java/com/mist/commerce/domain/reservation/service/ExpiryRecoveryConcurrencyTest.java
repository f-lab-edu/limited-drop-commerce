package com.mist.commerce.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.CommerceApplication;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.repository.EventItemOptionStockRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = CommerceApplication.class)
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExpiryRecoveryConcurrencyTest extends MySqlContainerTestSupport {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 100L;
    private static final Long EVENT_ITEM_ID = 1000L;
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);
    private static final int STOCK_QUANTITY = 10;
    private static final int RESERVED_QUANTITY = 3;
    private static final int REDIS_REMAINING_AFTER_RESERVE = 7;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private ExpiryRecoveryService expiryRecoveryService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private EventItemOptionStockRepository eventItemOptionStockRepository;

    @Autowired
    private OptionStockRedisRepository optionStockRedisRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update("delete from inventory_reservation");
        jdbcTemplate.update("delete from order_item");
        jdbcTemplate.update("delete from orders");
        jdbcTemplate.update("delete from event_item_option_stock");
    }

    @Test
    @DisplayName("TC-EXPIRY-CONCURRENT-001: 같은 만료 주문에 대한 동시 다중 recover는 재고를 한 번만 원복한다")
    void recover_whenCalledConcurrentlyForSameExpiredOrder_restoresStockOnlyOnce() throws InterruptedException {
        RecoveryFixture fixture = persistExpiredPendingRecoveryFixture();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    expiryRecoveryService.recover(fixture.orderId());
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(completed).isTrue();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStockId())).isEqualTo(10L);
        assertThat(reloadedOptionStock(fixture.optionStockId()).getReservedQuantity()).isZero();
        assertThat(orderRepository.findById(fixture.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reloadedReservation(fixture.reservationId()).getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    private RecoveryFixture persistExpiredPendingRecoveryFixture() {
        EventItemOptionStock optionStock = EventItemOptionStock.create(1L, 2L, STOCK_QUANTITY);
        optionStock.reserve(RESERVED_QUANTITY);
        EventItemOptionStock savedOptionStock = eventItemOptionStockRepository.saveAndFlush(optionStock);

        Order savedOrder = orderRepository.saveAndFlush(expiredPendingOrder(savedOptionStock.getId()));

        InventoryReservation reservation = InventoryReservation.create(
                savedOrder.getId(),
                EVENT_ITEM_ID,
                savedOptionStock.getId(),
                RESERVED_QUANTITY,
                LocalDateTime.now().minusMinutes(31),
                RESERVATION_TTL);
        InventoryReservation savedReservation = inventoryReservationRepository.saveAndFlush(reservation);
        optionStockRedisRepository.initialize(savedOptionStock.getId(), REDIS_REMAINING_AFTER_RESERVE);

        return new RecoveryFixture(savedOrder.getId(), savedReservation.getId(), savedOptionStock.getId());
    }

    private Order expiredPendingOrder(Long eventItemOptionId) {
        return Order.create(
                USER_ID,
                EVENT_ID,
                List.of(OrderItem.create(
                        EVENT_ITEM_ID,
                        eventItemOptionId,
                        "색상",
                        "Black",
                        new BigDecimal("150000"),
                        RESERVED_QUANTITY)),
                LocalDateTime.now().minusMinutes(31),
                PAYMENT_TTL);
    }

    private EventItemOptionStock reloadedOptionStock(Long optionStockId) {
        return eventItemOptionStockRepository.findById(optionStockId).orElseThrow();
    }

    private InventoryReservation reloadedReservation(Long reservationId) {
        return inventoryReservationRepository.findById(reservationId).orElseThrow();
    }

    private record RecoveryFixture(Long orderId, Long reservationId, Long optionStockId) {
    }
}
