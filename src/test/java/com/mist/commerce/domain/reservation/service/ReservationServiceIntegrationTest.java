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
import java.util.List;
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
        return new ReserveCommand(
                USER_ID,
                fixture.event().getId(),
                fixture.eventItem().getId(),
                fixture.optionStock().getId(),
                quantity);
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
