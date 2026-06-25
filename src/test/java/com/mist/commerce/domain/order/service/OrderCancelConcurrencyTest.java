package com.mist.commerce.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.CommerceApplication;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.repository.EventItemOptionStockRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderAlreadyCancelledException;
import com.mist.commerce.domain.order.exception.OrderCancelTemporarilyUnavailableException;
import com.mist.commerce.domain.order.exception.OrderCannotCancelException;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.redis.ReservationExpiryRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import com.mist.commerce.domain.reservation.service.ExpiryRecoveryService;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
class OrderCancelConcurrencyTest extends MySqlContainerTestSupport {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 100L;
    private static final Long EVENT_ITEM_ID = 1000L;
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);
    private static final int STOCK_QUANTITY = 10;
    private static final int RESERVED_QUANTITY = 3;
    private static final int REDIS_REMAINING_AFTER_RESERVE = 7;
    private static final String IDEMPOTENCY_KEY = "order-cancel-concurrency-key-001";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private OrderCancelService orderCancelService;

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
    private ReservationExpiryRedisRepository reservationExpiryRedisRepository;

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
    @DisplayName("TC-OC-CONCURRENT-01: 취소와 만료가 동시에 발생해도 재고는 한 번만 복구된다")
    void cancelAndExpire_whenRunConcurrently_restoreStockOnlyOnce() throws InterruptedException {
        RecoveryFixture fixture = persistPendingRecoveryFixture();
        reservationExpiryRedisRepository.markExpiry(fixture.orderId(), PAYMENT_TTL);
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();

        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                orderCancelService.cancel(new CancelCommand(USER_ID, fixture.orderId(), IDEMPOTENCY_KEY));
            } catch (OrderCannotCancelException
                     | OrderAlreadyCancelledException
                     | OrderCancelTemporarilyUnavailableException allowedRaceLoss) {
                // Losing the state guard race is an allowed outcome for the cancel path.
            } catch (Throwable throwable) {
                unexpectedFailures.add(throwable);
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            ready.countDown();
            try {
                start.await();
                expiryRecoveryService.recover(fixture.orderId());
            } catch (Throwable throwable) {
                unexpectedFailures.add(throwable);
            } finally {
                done.countDown();
            }
        });

        boolean readyCompleted = ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        boolean doneCompleted = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(readyCompleted).isTrue();
        assertThat(doneCompleted).isTrue();
        assertThat(unexpectedFailures).isEmpty();

        Order reloadedOrder = orderRepository.findById(fixture.orderId()).orElseThrow();
        EventItemOptionStock reloadedOptionStock = eventItemOptionStockRepository
                .findById(fixture.optionStockId())
                .orElseThrow();
        InventoryReservation reloadedReservation = inventoryReservationRepository
                .findById(fixture.reservationId())
                .orElseThrow();

        assertThat(reloadedOrder.getStatus()).isIn(OrderStatus.CANCELLED, OrderStatus.EXPIRED);
        assertThat(reloadedOrder.getCancelledAt() != null).isNotEqualTo(reloadedOrder.getExpiredAt() != null);
        assertThat(reloadedOptionStock.getReservedQuantity()).isZero();
        assertThat(reloadedOptionStock.getStockQuantity() - reloadedOptionStock.getReservedQuantity())
                .isEqualTo(STOCK_QUANTITY);
        assertThat(reloadedReservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reloadedReservation.getReleasedAt()).isNotNull();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStockId())).isEqualTo((long) STOCK_QUANTITY);
    }

    private RecoveryFixture persistPendingRecoveryFixture() {
        EventItemOptionStock optionStock = EventItemOptionStock.create(1L, 2L, STOCK_QUANTITY);
        optionStock.reserve(RESERVED_QUANTITY);
        EventItemOptionStock savedOptionStock = eventItemOptionStockRepository.saveAndFlush(optionStock);

        Order savedOrder = orderRepository.saveAndFlush(pendingOrder(savedOptionStock.getId()));

        InventoryReservation reservation = InventoryReservation.create(
                savedOrder.getId(),
                EVENT_ITEM_ID,
                savedOptionStock.getId(),
                RESERVED_QUANTITY,
                LocalDateTime.now().minusMinutes(10),
                RESERVATION_TTL);
        InventoryReservation savedReservation = inventoryReservationRepository.saveAndFlush(reservation);
        optionStockRedisRepository.initialize(savedOptionStock.getId(), REDIS_REMAINING_AFTER_RESERVE);

        return new RecoveryFixture(savedOrder.getId(), savedReservation.getId(), savedOptionStock.getId());
    }

    private Order pendingOrder(Long eventItemOptionId) {
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
                LocalDateTime.now().minusMinutes(10),
                PAYMENT_TTL);
    }

    private record RecoveryFixture(Long orderId, Long reservationId, Long optionStockId) {
    }
}
