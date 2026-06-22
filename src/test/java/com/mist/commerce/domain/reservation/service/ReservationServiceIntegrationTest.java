package com.mist.commerce.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.CommerceApplication;
import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.product.entity.Product;
import com.mist.commerce.domain.product.entity.ProductOptionGroup;
import com.mist.commerce.domain.product.entity.ProductOptionValue;
import com.mist.commerce.domain.product.entity.ProductStatus;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.product.repository.ProductRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = {CommerceApplication.class, ReservationServiceIntegrationTest.FixedClockConfig.class})
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReservationServiceIntegrationTest extends MySqlContainerTestSupport {

    private static final Long USER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "reservation-idem-key-001";
    private static final String CONCURRENT_IDEMPOTENCY_KEY = "reservation-idem-key-concurrent";
    private static final String ROLLBACK_IDEMPOTENCY_KEY = "reservation-idem-key-rollback";
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(FIXED_CLOCK);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionGroupRepository productOptionGroupRepository;

    @Autowired
    private ProductOptionValueRepository productOptionValueRepository;

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
        jdbcTemplate.update("delete from event_item");
        jdbcTemplate.update("delete from event");
        jdbcTemplate.update("delete from product_option_value");
        jdbcTemplate.update("delete from product_option_group");
        jdbcTemplate.update("delete from product");
        jdbcTemplate.update("delete from brand");
    }

    @Test
    @DisplayName("TC-RS-I-001: 예약 성공 시 주문, 선점, 옵션 재고 차감이 한 트랜잭션으로 저장된다")
    void reserve_whenValidCommand_persistsOrderReservationAndReservedStock() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");

        ReserveResult result = reservationService.reserve(command(fixture, 2));

        assertThat(result.expiresAt()).isEqualTo(NOW.plus(PAYMENT_TTL));
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());

        Order order = orderRepository.findById(result.orderId()).orElseThrow();
        assertThat(order.getUserId()).isEqualTo(USER_ID);
        assertThat(order.getEventId()).isEqualTo(fixture.event().getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getExpiresAt()).isEqualTo(NOW.plus(PAYMENT_TTL));
        assertThat(order.getItems()).hasSize(1);
        OrderItem orderItem = order.getItems().getFirst();
        assertThat(orderItem.getEventItemId()).isEqualTo(fixture.eventItem().getId());
        assertThat(orderItem.getEventItemOptionId()).isEqualTo(fixture.optionStock().getId());
        assertThat(orderItem.getProductOptionGroupName()).isEqualTo("색상");
        assertThat(orderItem.getProductOptionValueName()).isEqualTo("Black");
        assertThat(orderItem.getQuantity()).isEqualTo(2);

        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(order.getId());
        assertThat(reservations).hasSize(1);
        InventoryReservation reservation = reservations.getFirst();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getReservedQuantity()).isEqualTo(2);
        assertThat(reservation.getExpiresAt()).isEqualTo(NOW.plus(PAYMENT_TTL));

        EventItemOptionStock optionStock = reloadedOptionStock(fixture);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(8L);
    }

    @Test
    @DisplayName("TC-RS-EXPIRY-MARKER-001: reserve 커밋 성공 시 reservation:expiry:{orderId} 마커 키가 TTL과 함께 등록된다")
    void reserve_whenCommitSucceeds_registersExpiryMarker() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");

        ReserveResult result = reservationService.reserve(command(fixture, 2));

        String markerKey = "reservation:expiry:" + result.orderId();
        assertThat(redisTemplate.hasKey(markerKey)).isTrue();
        Long ttlSeconds = redisTemplate.getExpire(markerKey, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive();
        assertThat(ttlSeconds).isLessThanOrEqualTo(1800L);
    }

    @Test
    @DisplayName("TC-RS-I-002: 재고가 부족하면 주문과 선점이 저장되지 않고 옵션 재고 차감도 롤백된다")
    void reserve_whenInsufficientStock_rollsBackAllChanges() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 1, "색상", "Black");

        assertBusinessException("INSUFFICIENT_STOCK", () -> reservationService.reserve(command(fixture, 2)));

        assertNoOrderOrReservationStored();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("TC-RS-I-003: 가용 재고가 0이면 주문과 선점이 저장되지 않고 옵션 재고 차감도 롤백된다")
    void reserve_whenStockIsExhausted_rollsBackAllChanges() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 0, "색상", "Black");

        assertBusinessException("STOCK_EXHAUSTED", () -> reservationService.reserve(command(fixture, 1)));

        assertNoOrderOrReservationStored();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isZero();
    }

    @Test
    @DisplayName("TC-RS-I-004: 같은 사용자와 이벤트의 결제 대기 주문이 있으면 추가 주문과 선점이 저장되지 않는다")
    void reserve_whenActiveReservationAlreadyExists_doesNotCreateAdditionalRows() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");
        orderRepository.saveAndFlush(order(fixture));

        assertBusinessException(
                "ACTIVE_RESERVATION_ALREADY_EXISTS",
                () -> reservationService.reserve(command(fixture, 1)));

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(inventoryReservationRepository.findAll()).isEmpty();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isNull();
    }

    @Test
    @DisplayName("TC-RS-I-005: READY 이벤트면 주문과 선점이 저장되지 않고 재고가 유지된다")
    void reserve_whenEventIsReady_doesNotCreateRows() {
        Fixture fixture = persistFixture(EventStatus.READY, 10, "색상", "Black");

        assertBusinessException("DROP_EVENT_NOT_OPEN", () -> reservationService.reserve(command(fixture, 1)));

        assertNoOrderOrReservationStored();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isNull();
    }

    @Test
    @DisplayName("TC-RS-I-006: CLOSED 이벤트면 주문과 선점이 저장되지 않고 재고가 유지된다")
    void reserve_whenEventIsClosed_doesNotCreateRows() {
        Fixture fixture = persistFixture(EventStatus.CLOSED, 10, "색상", "Black");

        assertBusinessException("DROP_EVENT_CLOSED", () -> reservationService.reserve(command(fixture, 1)));

        assertNoOrderOrReservationStored();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isNull();
    }

    @Test
    @DisplayName("TC-RS-I-007: 재고 reserve 후 옵션 이름 해소 실패 시 주문, 선점, 재고 차감이 모두 롤백된다")
    void reserve_whenOptionNameResolutionFailsAfterStockReserve_rollsBackAllChanges() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");
        productOptionValueRepository.deleteById(fixture.optionValue().getId());

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> reservationService.reserve(command(fixture, 2)));

        assertNoOrderOrReservationStored();
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("TC-RS-IDEM-I-001: 최초 예약은 주문을 생성하고 멱등성 키를 DONE으로 기록한다")
    void reserve_withNewIdempotencyKey_createsOrderAndCompletesIdempotencyRecord() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");

        ReserveResult result = reservationService.reserve(command(fixture, 2, IDEMPOTENCY_KEY));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(inventoryReservationRepository.findAll()).hasSize(1);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(8L);
        assertThat(redisTemplate.opsForValue().get(idempotencyRedisKey(IDEMPOTENCY_KEY))).contains("DONE");
        assertThat(redisTemplate.getExpire(idempotencyRedisKey(IDEMPOTENCY_KEY), TimeUnit.SECONDS)).isPositive();
    }

    @Test
    @DisplayName("TC-RS-IDEM-I-002: 동일 키와 동일 바디 재시도는 저장된 결과를 반환하고 주문과 재고를 추가 변경하지 않는다")
    void reserve_withSameIdempotencyKeyAndSameBody_replaysStoredResultWithoutSideEffects() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");
        ReserveCommand command = command(fixture, 2, IDEMPOTENCY_KEY);
        ReserveResult first = reservationService.reserve(command);
        Long stockAfterFirst = optionStockRedisRepository.getRemaining(fixture.optionStock().getId());
        int orderCountAfterFirst = orderRepository.findAll().size();
        int reservationCountAfterFirst = inventoryReservationRepository.findAll().size();

        ReserveResult second = reservationService.reserve(command);

        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(orderRepository.findAll()).hasSize(orderCountAfterFirst);
        assertThat(inventoryReservationRepository.findAll()).hasSize(reservationCountAfterFirst);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(stockAfterFirst);
    }

    @Test
    @DisplayName("TC-RS-IDEM-I-003: 동일 키와 다른 바디 재시도는 IDEMPOTENCY_KEY_REUSED를 던지고 부작용이 없다")
    void reserve_withSameIdempotencyKeyAndDifferentBody_throwsIdempotencyKeyReusedWithoutSideEffects() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");
        reservationService.reserve(command(fixture, 2, IDEMPOTENCY_KEY));
        Long stockAfterFirst = optionStockRedisRepository.getRemaining(fixture.optionStock().getId());
        int orderCountAfterFirst = orderRepository.findAll().size();
        int reservationCountAfterFirst = inventoryReservationRepository.findAll().size();

        assertBusinessException("IDEMPOTENCY_KEY_REUSED", () -> reservationService.reserve(command(fixture, 3, IDEMPOTENCY_KEY)));

        assertThat(orderRepository.findAll()).hasSize(orderCountAfterFirst);
        assertThat(inventoryReservationRepository.findAll()).hasSize(reservationCountAfterFirst);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(stockAfterFirst);
        assertThat(reloadedOptionStock(fixture).getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-RS-IDEM-I-004: 동일 키 동시 요청은 주문을 하나만 생성하고 재고를 한 번만 차감한다")
    void reserve_concurrentlyWithSameIdempotencyKey_createsOnlyOneOrder() throws Exception {
        Fixture fixture = persistFixture(EventStatus.OPEN, 10, "색상", "Black");
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger inProgressCount = new AtomicInteger();
        AtomicInteger unexpectedFailureCount = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();

                try {
                    ReserveResult result = reservationService.reserve(command(fixture, 1, CONCURRENT_IDEMPOTENCY_KEY));
                    if (result.orderId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (BusinessException ex) {
                    if ("RESERVATION_IN_PROGRESS".equals(ex.getCode())) {
                        inProgressCount.incrementAndGet();
                    } else {
                        unexpectedFailureCount.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(inventoryReservationRepository.findAll()).hasSize(1);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(9L);
        assertThat(successCount.get() + inProgressCount.get()).isEqualTo(threadCount);
        assertThat(unexpectedFailureCount.get()).isZero();
    }

    @Test
    @DisplayName("TC-RS-IDEM-I-005: 예약 롤백 후 멱등성 키가 release되어 동일 키 재시도가 IN_PROGRESS로 막히지 않는다")
    void reserve_whenTransactionRollsBack_releasesIdempotencyKeyForRetry() {
        Fixture fixture = persistFixture(EventStatus.OPEN, 1, "색상", "Black");

        assertBusinessException("INSUFFICIENT_STOCK", () -> reservationService.reserve(command(fixture, 2, ROLLBACK_IDEMPOTENCY_KEY)));
        assertNoOrderOrReservationStored();

        jdbcTemplate.update("update event_item_option_stock set stock_quantity = ? where id = ?", 10, fixture.optionStock().getId());
        optionStockRedisRepository.initialize(fixture.optionStock().getId(), 10);
        ReserveResult retry = reservationService.reserve(command(fixture, 2, ROLLBACK_IDEMPOTENCY_KEY));

        assertThat(retry.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(inventoryReservationRepository.findAll()).hasSize(1);
        assertThat(optionStockRedisRepository.getRemaining(fixture.optionStock().getId())).isEqualTo(8L);
    }

    private void assertNoOrderOrReservationStored() {
        assertThat(orderRepository.findAll()).isEmpty();
        assertThat(inventoryReservationRepository.findAll()).isEmpty();
    }

    private void assertBusinessException(String expectedCode, ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private ReserveCommand command(Fixture fixture, int quantity) {
        return command(fixture, quantity, IDEMPOTENCY_KEY);
    }

    private ReserveCommand command(Fixture fixture, int quantity, String idempotencyKey) {
        return new ReserveCommand(
                USER_ID,
                fixture.event().getId(),
                fixture.eventItem().getId(),
                fixture.optionStock().getId(),
                quantity,
                idempotencyKey);
    }

    private String idempotencyRedisKey(String idempotencyKey) {
        return "idem:" + USER_ID + ":" + idempotencyKey;
    }

    private Order order(Fixture fixture) {
        return Order.create(
                USER_ID,
                fixture.event().getId(),
                List.of(OrderItem.create(
                        fixture.eventItem().getId(),
                        fixture.optionStock().getId(),
                        "색상",
                        "Black",
                        new BigDecimal("150000"),
                        1)),
                NOW,
                PAYMENT_TTL);
    }

    private Fixture persistFixture(EventStatus eventStatus, int stockQuantity, String groupName, String valueName) {
        Product product = productRepository.saveAndFlush(product(groupName, valueName));
        ProductOptionGroup group = product.getOptionGroups().getFirst();
        ProductOptionValue value = group.getOptionValues().getFirst();

        EventItemOptionStock optionStock = EventItemOptionStock.create(group.getId(), value.getId(), stockQuantity);
        EventItem eventItem = EventItem.create(
                product.getId(),
                new BigDecimal("150000"),
                stockQuantity,
                10,
                List.of(optionStock));
        Event event = Event.create(
                1L,
                "한정 스니커즈 드롭",
                Instant.parse("2026-06-17T00:00:00Z"),
                Instant.parse("2026-06-17T06:00:00Z"),
                List.of(eventItem));
        ReflectionTestUtils.setField(event, "status", eventStatus);

        Event savedEvent = eventRepository.saveAndFlush(event);
        return new Fixture(savedEvent, savedEvent.getItems().getFirst(), savedEvent.getItems().getFirst().getOptionStocks().getFirst(),
                group, value);
    }

    private Product product(String groupName, String valueName) {
        Long brandId = persistBrand();
        return Product.create(
                brandId,
                1L,
                "Limited Sneakers",
                "2026 한정판",
                150_000L,
                ProductStatus.READY,
                List.of(new Product.OptionGroupSpec(groupName, 0, true, List.of(valueName))));
    }

    private Long persistBrand() {
        jdbcTemplate.update("insert into brand (company_id, name) values (?, ?)", 1L, "Mist");
        return jdbcTemplate.queryForObject("select id from brand where name = ?", Long.class, "Mist");
    }

    private EventItemOptionStock reloadedOptionStock(Fixture fixture) {
        return eventRepository.findById(fixture.event().getId())
                .orElseThrow()
                .getItems()
                .getFirst()
                .getOptionStocks()
                .getFirst();
    }

    private record Fixture(
            Event event,
            EventItem eventItem,
            EventItemOptionStock optionStock,
            ProductOptionGroup optionGroup,
            ProductOptionValue optionValue
    ) {
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock clock() {
            return FIXED_CLOCK;
        }
    }
}
