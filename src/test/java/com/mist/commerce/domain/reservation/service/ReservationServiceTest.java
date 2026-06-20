package com.mist.commerce.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.product.entity.ProductOptionGroup;
import com.mist.commerce.domain.product.entity.ProductOptionValue;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import com.mist.commerce.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 20L;
    private static final Long EVENT_ITEM_ID = 30L;
    private static final Long OPTION_STOCK_ID = 40L;
    private static final Long PRODUCT_OPTION_GROUP_ID = 50L;
    private static final Long PRODUCT_OPTION_VALUE_ID = 60L;
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private ProductOptionGroupRepository productOptionGroupRepository;

    @Mock
    private ProductOptionValueRepository productOptionValueRepository;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                eventRepository,
                orderRepository,
                inventoryReservationRepository,
                productOptionGroupRepository,
                productOptionValueRepository,
                CLOCK);
    }

    @Test
    @DisplayName("TC-RS-U-001: 예약 성공 시 주문과 재고 선점을 저장하고 결제 대기 결과를 반환한다")
    void reserve_whenCommandIsValid_savesOrderAndReservationAndReturnsPendingPaymentResult() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        ProductOptionGroup group = optionGroup("색상");
        ProductOptionValue value = optionValue("Black");
        Order savedOrder = savedOrder(1000L);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(orderRepository.existsByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, OrderStatus.PENDING_PAYMENT))
                .thenReturn(false);
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.of(group));
        when(productOptionValueRepository.findById(PRODUCT_OPTION_VALUE_ID)).thenReturn(Optional.of(value));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        when(inventoryReservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReserveResult result = reservationService.reserve(command(2));

        assertThat(result.orderId()).isEqualTo(1000L);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(PAYMENT_TTL));
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT.name());
        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isEqualTo(2);

        InOrder ordered = inOrder(
                eventRepository,
                orderRepository,
                productOptionGroupRepository,
                productOptionValueRepository,
                inventoryReservationRepository);
        ordered.verify(eventRepository).findById(EVENT_ID);
        ordered.verify(orderRepository).existsByUserIdAndEventIdAndStatus(
                USER_ID,
                EVENT_ID,
                OrderStatus.PENDING_PAYMENT);
        ordered.verify(productOptionGroupRepository).findById(PRODUCT_OPTION_GROUP_ID);
        ordered.verify(productOptionValueRepository).findById(PRODUCT_OPTION_VALUE_ID);
        ordered.verify(orderRepository).save(any(Order.class));
        ordered.verify(inventoryReservationRepository).save(any(InventoryReservation.class));
    }

    @Test
    @DisplayName("TC-RS-U-002: 이벤트가 없으면 DROP_EVENT_NOT_FOUND를 던지고 후속 처리를 하지 않는다")
    void reserve_whenEventDoesNotExist_throwsDropEventNotFound() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertBusinessException("DROP_EVENT_NOT_FOUND", () -> reservationService.reserve(command(1)));

        verify(orderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-003: READY 이벤트면 DROP_EVENT_NOT_OPEN을 던지고 후속 처리를 하지 않는다")
    void reserve_whenEventIsReady_throwsDropEventNotOpen() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(readyEvent()));

        assertBusinessException("DROP_EVENT_NOT_OPEN", () -> reservationService.reserve(command(1)));

        verify(orderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-004: CLOSED 이벤트면 DROP_EVENT_CLOSED를 던지고 후속 처리를 하지 않는다")
    void reserve_whenEventIsClosed_throwsDropEventClosed() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(closedEvent()));

        assertBusinessException("DROP_EVENT_CLOSED", () -> reservationService.reserve(command(1)));

        verify(orderRepository, never()).existsByUserIdAndEventIdAndStatus(any(), any(), any());
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-005: 같은 사용자와 이벤트에 결제 대기 주문이 있으면 ACTIVE_RESERVATION_ALREADY_EXISTS를 던진다")
    void reserve_whenPendingOrderAlreadyExists_throwsActiveReservationAlreadyExists() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(orderRepository.existsByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, OrderStatus.PENDING_PAYMENT))
                .thenReturn(true);

        assertBusinessException("ACTIVE_RESERVATION_ALREADY_EXISTS", () -> reservationService.reserve(command(1)));

        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isZero();
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-006: 이벤트 상품이 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void reserve_whenEventItemDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(999L, 3, 10, optionStock(10)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> reservationService.reserve(command(1)));

        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-007: 옵션 재고가 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void reserve_whenOptionStockDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(3, 10, optionStock(999L, 10)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> reservationService.reserve(command(1)));

        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isZero();
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-008: 수량이 0이면 INVALID_RESERVATION_QUANTITY를 던진다")
    void reserve_whenQuantityIsZero_throwsInvalidReservationQuantity() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("INVALID_RESERVATION_QUANTITY", () -> reservationService.reserve(command(0)));

        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isZero();
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-009: 수량이 음수이면 INVALID_RESERVATION_QUANTITY를 던진다")
    void reserve_whenQuantityIsNegative_throwsInvalidReservationQuantity() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("INVALID_RESERVATION_QUANTITY", () -> reservationService.reserve(command(-1)));

        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isZero();
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-010: 구매 제한을 초과하면 PURCHASE_LIMIT_EXCEEDED를 던진다")
    void reserve_whenQuantityExceedsPurchaseLimit_throwsPurchaseLimitExceeded() {
        Event event = openEvent(item(1, 10, optionStock(10)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("PURCHASE_LIMIT_EXCEEDED", () -> reservationService.reserve(command(2)));

        assertThat(event.getItems().getFirst().getOptionStocks().getFirst().getReservedQuantity()).isZero();
        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-011: 가용 재고가 부족하면 INSUFFICIENT_STOCK을 던진다")
    void reserve_whenAvailableStockIsLowerThanQuantity_throwsInsufficientStock() {
        Event event = openEvent(item(3, 10, optionStock(1)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("INSUFFICIENT_STOCK", () -> reservationService.reserve(command(2)));

        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC-RS-U-012: 가용 재고가 0이면 STOCK_EXHAUSTED를 던진다")
    void reserve_whenAvailableStockIsZero_throwsStockExhausted() {
        Event event = openEvent(item(3, 10, optionStock(0)));
        stubOpenEventWithoutDuplicate(event);

        assertBusinessException("STOCK_EXHAUSTED", () -> reservationService.reserve(command(1)));

        verifyNoInteractions(productOptionGroupRepository, productOptionValueRepository, inventoryReservationRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("8단계: 옵션 그룹 이름을 해소할 수 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void reserve_whenProductOptionGroupDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        stubOpenEventWithoutDuplicate(event);
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.empty());

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> reservationService.reserve(command(1)));

        verify(productOptionValueRepository, never()).findById(any());
        verify(orderRepository, never()).save(any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("8단계: 옵션 값 이름을 해소할 수 없으면 EVENT_ITEM_OPTION_NOT_FOUND를 던진다")
    void reserve_whenProductOptionValueDoesNotExist_throwsEventItemOptionNotFound() {
        Event event = openEvent(item(3, 10, optionStock(10)));
        ProductOptionGroup group = optionGroup("색상");
        stubOpenEventWithoutDuplicate(event);
        when(productOptionGroupRepository.findById(PRODUCT_OPTION_GROUP_ID)).thenReturn(Optional.of(group));
        when(productOptionValueRepository.findById(PRODUCT_OPTION_VALUE_ID)).thenReturn(Optional.empty());

        assertBusinessException("EVENT_ITEM_OPTION_NOT_FOUND", () -> reservationService.reserve(command(1)));

        verify(orderRepository, never()).save(any());
        verify(inventoryReservationRepository, never()).save(any());
    }

    private void stubOpenEventWithoutDuplicate(Event event) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(orderRepository.existsByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, OrderStatus.PENDING_PAYMENT))
                .thenReturn(false);
    }

    private void assertBusinessException(String expectedCode, ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private ReserveCommand command(int quantity) {
        return new ReserveCommand(USER_ID, EVENT_ID, EVENT_ITEM_ID, OPTION_STOCK_ID, quantity);
    }

    private Event readyEvent() {
        return event(EventStatus.READY, item(3, 10, optionStock(10)));
    }

    private Event closedEvent() {
        return event(EventStatus.CLOSED, item(3, 10, optionStock(10)));
    }

    private Event openEvent(EventItem eventItem) {
        return event(EventStatus.OPEN, eventItem);
    }

    private Event event(EventStatus status, EventItem eventItem) {
        Event event = Event.create(
                1L,
                "한정 스니커즈 드롭",
                Instant.parse("2026-06-17T00:00:00Z"),
                Instant.parse("2026-06-17T06:00:00Z"),
                List.of(eventItem));
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }

    private EventItem item(int maxPurchasePerCustomer, int quantity, EventItemOptionStock optionStock) {
        return item(EVENT_ITEM_ID, maxPurchasePerCustomer, quantity, optionStock);
    }

    private EventItem item(
            Long eventItemId,
            int maxPurchasePerCustomer,
            int quantity,
            EventItemOptionStock optionStock
    ) {
        EventItem eventItem = EventItem.create(
                100L,
                new BigDecimal("150000"),
                quantity,
                maxPurchasePerCustomer,
                List.of(optionStock));
        ReflectionTestUtils.setField(eventItem, "id", eventItemId);
        return eventItem;
    }

    private EventItemOptionStock optionStock(int stockQuantity) {
        return optionStock(OPTION_STOCK_ID, stockQuantity);
    }

    private EventItemOptionStock optionStock(Long optionStockId, int stockQuantity) {
        EventItemOptionStock optionStock = EventItemOptionStock.create(
                PRODUCT_OPTION_GROUP_ID,
                PRODUCT_OPTION_VALUE_ID,
                stockQuantity);
        ReflectionTestUtils.setField(optionStock, "id", optionStockId);
        return optionStock;
    }

    private ProductOptionGroup optionGroup(String name) {
        ProductOptionGroup group = mock(ProductOptionGroup.class);
        when(group.getName()).thenReturn(name);
        return group;
    }

    private ProductOptionValue optionValue(String value) {
        ProductOptionValue optionValue = mock(ProductOptionValue.class);
        when(optionValue.getValue()).thenReturn(value);
        return optionValue;
    }

    private Order savedOrder(Long orderId) {
        Order order = Order.create(
                USER_ID,
                EVENT_ID,
                List.of(),
                NOW,
                PAYMENT_TTL);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
