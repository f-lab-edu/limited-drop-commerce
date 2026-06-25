package com.mist.commerce.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.repository.EventItemOptionStockRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderAlreadyCancelledException;
import com.mist.commerce.domain.order.exception.OrderCancelTemporarilyUnavailableException;
import com.mist.commerce.domain.order.exception.OrderCannotCancelException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.redis.ClaimResult;
import com.mist.commerce.domain.reservation.redis.ClaimStatus;
import com.mist.commerce.domain.reservation.redis.IdempotencyRedisRepository;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.redis.ReservationExpiryRedisRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    private static final Long ORDER_ID = 100L;
    private static final Long EVENT_ID = 200L;
    private static final Long EVENT_ITEM_ID = 300L;
    private static final Long OPTION_STOCK_ID = 400L;
    private static final int RESERVED_QUANTITY = 3;
    private static final String IDEMPOTENCY_KEY = "order-cancel-idem-key-001";
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String COMPLETED_CANCEL_PAYLOAD =
            "{\"orderId\":100,\"status\":\"CANCELLED\",\"cancelledAt\":\"2026-06-17T12:00:00\"}";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryReservationRepository inventoryReservationRepository;

    @Mock
    private EventItemOptionStockRepository eventItemOptionStockRepository;

    @Mock
    private OptionStockRedisRepository optionStockRedisRepository;

    @Mock
    private ReservationExpiryRedisRepository reservationExpiryRedisRepository;

    @Mock
    private IdempotencyRedisRepository idempotencyRedisRepository;

    private OrderCancelService orderCancelService;

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.CLAIMED, null));
        orderCancelService = new OrderCancelService(
                orderRepository,
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository,
                idempotencyRedisRepository,
                CLOCK);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("TC-OC-SVC-01: 본인 PENDING_PAYMENT 주문 취소 성공 시 주문과 재고를 복구한다")
    void cancel_whenPendingPaymentOrderBelongsToUser_restoresDbStockReservationAndRedisAfterCommit() {
        Order order = pendingOrder(USER_ID);
        InventoryReservation reservation = reservedInventoryReservation();
        EventItemOptionStock optionStock = reservedOptionStock();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.cancelIfPending(
                eq(ORDER_ID),
                eq(NOW),
                eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CANCELLED))).thenReturn(1);
        when(inventoryReservationRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(reservation));
        when(eventItemOptionStockRepository.findById(OPTION_STOCK_ID)).thenReturn(Optional.of(optionStock));
        TransactionSynchronizationManager.initSynchronization();

        CancelResult result = orderCancelService.cancel(command());

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(result.cancelledAt()).isEqualTo(NOW);
        assertThat(optionStock.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isEqualTo(NOW);
        verify(optionStockRedisRepository, never()).increase(any(), anyInt());
        verify(reservationExpiryRedisRepository, never()).clearExpiry(any());

        triggerAfterCommit();

        verify(optionStockRedisRepository).increase(OPTION_STOCK_ID, RESERVED_QUANTITY);
        verify(reservationExpiryRedisRepository).clearExpiry(ORDER_ID);
        verify(idempotencyRedisRepository).complete(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), any());
        InOrder afterCommitOrder = inOrder(
                optionStockRedisRepository,
                reservationExpiryRedisRepository,
                idempotencyRedisRepository);
        afterCommitOrder.verify(optionStockRedisRepository).increase(OPTION_STOCK_ID, RESERVED_QUANTITY);
        afterCommitOrder.verify(reservationExpiryRedisRepository).clearExpiry(ORDER_ID);
        afterCommitOrder.verify(idempotencyRedisRepository).complete(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), any());
    }

    @Test
    @DisplayName("TC-OC-SVC-02: 주문이 없으면 ORDER_NOT_FOUND")
    void cancel_whenOrderDoesNotExist_throwsOrderNotFoundWithoutSideEffects() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertBusinessException(OrderNotFoundException.class, "ORDER_NOT_FOUND", () -> orderCancelService.cancel(command()));

        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verify(orderRepository, never()).cancelIfPending(any(), any(), any(), any());
        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
    }

    @Test
    @DisplayName("TC-OC-SVC-03: 본인 주문이 아니면 ORDER_FORBIDDEN")
    void cancel_whenOrderBelongsToAnotherUser_throwsOrderForbiddenWithoutSideEffects() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(OTHER_USER_ID)));

        assertBusinessException(OrderForbiddenException.class, "ORDER_FORBIDDEN", () -> orderCancelService.cancel(command()));

        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verify(orderRepository, never()).cancelIfPending(any(), any(), any(), any());
        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
    }

    @Test
    @DisplayName("TC-OC-SVC-04: 이미 취소된 주문이면 ORDER_ALREADY_CANCELLED")
    void cancel_whenOrderAlreadyCancelled_throwsOrderAlreadyCancelledWithoutRestoringAgain() {
        LocalDateTime existingCancelledAt = NOW.minusMinutes(1);
        Order order = pendingOrder(USER_ID);
        order.cancel(existingCancelledAt);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertBusinessException(
                OrderAlreadyCancelledException.class,
                "ORDER_ALREADY_CANCELLED",
                () -> orderCancelService.cancel(command()));

        assertThat(order.getCancelledAt()).isEqualTo(existingCancelledAt);
        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verify(orderRepository, never()).cancelIfPending(any(), any(), any(), any());
        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
    }

    @Test
    @DisplayName("TC-OC-SVC-05: PAID, EXPIRED, PAYMENT_FAILED 주문이면 ORDER_CANNOT_CANCEL")
    void cancel_whenOrderStatusCannotCancel_throwsOrderCannotCancelWithoutRestoring() {
        for (OrderStatus status : List.of(OrderStatus.PAID, OrderStatus.EXPIRED, OrderStatus.PAYMENT_FAILED)) {
            Order order = orderWithStatus(status);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertBusinessException(OrderCannotCancelException.class, "ORDER_CANNOT_CANCEL", () -> orderCancelService.cancel(command()));

            assertThat(order.getCancelledAt()).isNull();
            verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
            verify(orderRepository, never()).cancelIfPending(any(), any(), any(), any());
            verifyNoInteractions(
                    inventoryReservationRepository,
                    eventItemOptionStockRepository,
                    optionStockRedisRepository,
                    reservationExpiryRedisRepository);
        }
    }

    @Test
    @DisplayName("TC-OC-SVC-06: 동일 Idempotency-Key 재요청은 원래 결과를 반환하고 재고를 한 번만 복구한다")
    void cancel_whenIdempotencyClaimCompleted_returnsStoredResultWithoutRestoringAgain() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(pendingOrder(USER_ID)));
        when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.COMPLETED, COMPLETED_CANCEL_PAYLOAD));

        CancelResult result = orderCancelService.cancel(command());

        assertThat(result.orderId()).isEqualTo(ORDER_ID);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(result.cancelledAt()).isEqualTo(NOW);
        verify(orderRepository, never()).cancelIfPending(any(), any(), any(), any());
        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
    }

    @Test
    @DisplayName("TC-OC-SVC-07: 취소와 만료가 동시에 발생해도 상태 전이와 재고 복구는 1회만 성공한다")
    void cancel_whenExpireWinsRace_throwsCannotCancelAndDoesNotRestoreStockAgain() {
        Order initiallyPending = pendingOrder(USER_ID);
        Order expiredAfterRace = pendingOrder(USER_ID);
        expiredAfterRace.expire(NOW);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(initiallyPending))
                .thenReturn(Optional.of(expiredAfterRace));
        when(orderRepository.cancelIfPending(
                eq(ORDER_ID),
                eq(NOW),
                eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CANCELLED))).thenReturn(0);

        assertBusinessException(OrderCannotCancelException.class, "ORDER_CANNOT_CANCEL", () -> orderCancelService.cancel(command()));

        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
    }

    @Test
    @DisplayName("TC-OC-SVC-08: cancelIfPending 0건이면 재조회 상태에 따라 예외를 매핑한다")
    void cancel_whenCancelIfPendingAffectsNoRows_mapsReloadedStateToSpecificException() {
        assertRaceMapping(cancelledOrder(), OrderAlreadyCancelledException.class, "ORDER_ALREADY_CANCELLED");
        assertRaceMapping(orderWithStatus(OrderStatus.PAID), OrderCannotCancelException.class, "ORDER_CANNOT_CANCEL");
        assertRaceMapping(orderWithStatus(OrderStatus.EXPIRED), OrderCannotCancelException.class, "ORDER_CANNOT_CANCEL");
        assertRaceMapping(orderWithStatus(OrderStatus.PAYMENT_FAILED), OrderCannotCancelException.class, "ORDER_CANNOT_CANCEL");
        assertRaceMapping(pendingOrder(USER_ID), OrderCancelTemporarilyUnavailableException.class,
                "ORDER_CANCEL_TEMPORARILY_UNAVAILABLE");
    }

    private void assertRaceMapping(
            Order reloadedOrder,
            Class<? extends BusinessException> expectedType,
            String expectedCode
    ) {
        Order initiallyPending = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID))
                .thenReturn(Optional.of(initiallyPending))
                .thenReturn(Optional.of(reloadedOrder));
        when(orderRepository.cancelIfPending(
                eq(ORDER_ID),
                eq(NOW),
                eq(OrderStatus.PENDING_PAYMENT),
                eq(OrderStatus.CANCELLED))).thenReturn(0);

        assertBusinessException(expectedType, expectedCode, () -> orderCancelService.cancel(command()));

        verifyNoInteractions(
                inventoryReservationRepository,
                eventItemOptionStockRepository,
                optionStockRedisRepository,
                reservationExpiryRedisRepository);
        org.mockito.Mockito.reset(orderRepository, inventoryReservationRepository, eventItemOptionStockRepository,
                optionStockRedisRepository, reservationExpiryRedisRepository, idempotencyRedisRepository);
        lenient().when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.CLAIMED, null));
    }

    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    private CancelCommand command() {
        return new CancelCommand(USER_ID, ORDER_ID, IDEMPOTENCY_KEY);
    }

    private Order pendingOrder(Long userId) {
        Order order = Order.create(
                userId,
                EVENT_ID,
                List.of(OrderItem.create(
                        EVENT_ITEM_ID,
                        OPTION_STOCK_ID,
                        "색상",
                        "Black",
                        new BigDecimal("150000"),
                        RESERVED_QUANTITY)),
                NOW.minusMinutes(10),
                PAYMENT_TTL);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private Order cancelledOrder() {
        Order order = pendingOrder(USER_ID);
        order.cancel(NOW.minusSeconds(1));
        return order;
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = pendingOrder(USER_ID);
        if (status == OrderStatus.PAID) {
            order.markPaid();
            return order;
        }
        if (status == OrderStatus.EXPIRED) {
            order.expire(NOW.minusSeconds(1));
            return order;
        }
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private InventoryReservation reservedInventoryReservation() {
        InventoryReservation reservation = InventoryReservation.create(
                ORDER_ID,
                EVENT_ITEM_ID,
                OPTION_STOCK_ID,
                RESERVED_QUANTITY,
                NOW.minusMinutes(10),
                RESERVATION_TTL);
        ReflectionTestUtils.setField(reservation, "id", 500L);
        return reservation;
    }

    private EventItemOptionStock reservedOptionStock() {
        EventItemOptionStock optionStock = EventItemOptionStock.create(1L, 2L, 10);
        ReflectionTestUtils.setField(optionStock, "id", OPTION_STOCK_ID);
        optionStock.reserve(RESERVED_QUANTITY);
        return optionStock;
    }

    private void assertBusinessException(
            Class<? extends BusinessException> expectedType,
            String expectedCode,
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(expectedType)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
