package com.mist.commerce.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Long USER_ID = 10L;
    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 6, 17, 12, 0);
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

    @Test
    @DisplayName("TC-ORDER-001: OrderStatus가 주문 도메인 상태값을 모두 제공한다")
    void orderStatus_containsDefinedDomainStatusesOnly() {
        assertThat(Arrays.asList(OrderStatus.values()))
                .containsExactlyInAnyOrder(
                        OrderStatus.PENDING_PAYMENT,
                        OrderStatus.PAID,
                        OrderStatus.PAYMENT_FAILED,
                        OrderStatus.EXPIRED,
                        OrderStatus.CANCELLED);
        assertThatThrownBy(() -> OrderStatus.valueOf("PAYMENT_PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OrderStatus.valueOf("CANCELED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-ORDER-002: 주문 생성 시 결제 대기 상태와 주문 식별 정보가 설정된다")
    void create_setsPendingPaymentStatusAndOrderMetadata() {
        Order order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getOrderNo()).isNotBlank();
        assertThat(order.getOrderedAt()).isEqualTo(ORDERED_AT);
        assertThat(order.getExpiresAt()).isEqualTo(ORDERED_AT.plus(PAYMENT_TTL));
        assertThat(order.getExpiresAt()).isAfter(order.getOrderedAt());
        assertThat(order.getCancelledAt()).isNull();
        assertThat(order.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-003: 주문 생성 시 OrderItem 목록으로부터 총 금액과 총 수량을 계산한다")
    void create_calculatesTotalAmountAndTotalQuantityFromItems() {
        Order order = Order.create(
                USER_ID,
                List.of(
                        item(1L, 11L, "10000", 2),
                        item(2L, 22L, "25000", 3)),
                ORDERED_AT,
                PAYMENT_TTL);

        assertThat(order.getTotalAmount()).isEqualByComparingTo("95000");
        assertThat(order.getTotalQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-ORDER-004: 여러 주문을 생성하면 orderNo가 주문별로 고유하다")
    void create_generatesDifferentOrderNoForEachOrder() {
        Order first = order();
        Order second = order();

        assertThat(first.getOrderNo()).isNotBlank();
        assertThat(second.getOrderNo()).isNotBlank();
        assertThat(first.getOrderNo()).isNotEqualTo(second.getOrderNo());
    }

    @Test
    @DisplayName("TC-ORDER-006: 결제 대기 주문을 결제 완료 처리하면 PAID 상태가 된다")
    void markPaid_whenPendingPayment_changesStatusToPaid() {
        Order order = order();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelledAt()).isNull();
        assertThat(order.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-007: 결제 대기 주문을 만료 처리하면 EXPIRED 상태가 되고 expiredAt이 기록된다")
    void expire_whenPendingPayment_changesStatusToExpiredAndRecordsExpiredAt() {
        Order order = order();
        LocalDateTime expiredAt = ORDERED_AT.plusMinutes(31);

        order.expire(expiredAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-008: 결제 대기 주문을 취소하면 CANCELLED 상태가 되고 cancelledAt이 기록된다")
    void cancel_whenPendingPayment_changesStatusToCancelledAndRecordsCancelledAt() {
        Order order = order();
        LocalDateTime cancelledAt = ORDERED_AT.plusMinutes(10);

        order.cancel(cancelledAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(order.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-009: 결제 완료 주문도 취소할 수 있다")
    void cancel_whenPaid_changesStatusToCancelledAndRecordsCancelledAt() {
        Order order = paidOrder();
        LocalDateTime cancelledAt = ORDERED_AT.plusMinutes(10);

        order.cancel(cancelledAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(cancelledAt);
        assertThat(order.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-010: 결제 완료 주문은 만료 처리할 수 없다")
    void expire_whenPaid_throwsIllegalStateExceptionAndKeepsState() {
        Order order = paidOrder();

        assertThatThrownBy(() -> order.expire(ORDERED_AT.plusMinutes(31)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getExpiredAt()).isNull();
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-011: 만료된 주문은 결제 완료 처리할 수 없다")
    void markPaid_whenExpired_throwsIllegalStateExceptionAndKeepsState() {
        Order order = expiredOrder();
        LocalDateTime originalExpiredAt = order.getExpiredAt();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(originalExpiredAt);
        assertThat(order.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-012: 취소된 주문은 결제 완료 처리할 수 없다")
    void markPaid_whenCancelled_throwsIllegalStateExceptionAndKeepsState() {
        Order order = cancelledOrderFromPending();
        LocalDateTime originalCancelledAt = order.getCancelledAt();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(originalCancelledAt);
        assertThat(order.getExpiredAt()).isNull();
    }

    @Test
    @DisplayName("TC-ORDER-013: 취소된 주문은 다시 취소할 수 없다")
    void cancel_whenAlreadyCancelled_throwsIllegalStateExceptionAndKeepsState() {
        Order order = cancelledOrderFromPending();
        LocalDateTime originalCancelledAt = order.getCancelledAt();

        assertThatThrownBy(() -> order.cancel(ORDERED_AT.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(originalCancelledAt);
    }

    @Test
    @DisplayName("TC-ORDER-014: 만료된 주문은 취소할 수 없다")
    void cancel_whenExpired_throwsIllegalStateExceptionAndKeepsState() {
        Order order = expiredOrder();
        LocalDateTime originalExpiredAt = order.getExpiredAt();

        assertThatThrownBy(() -> order.cancel(ORDERED_AT.plusMinutes(40)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getExpiredAt()).isEqualTo(originalExpiredAt);
        assertThat(order.getCancelledAt()).isNull();
    }

    private Order order() {
        return Order.create(USER_ID, List.of(item(1L, 11L, "150000", 1)), ORDERED_AT, PAYMENT_TTL);
    }

    private Order paidOrder() {
        Order order = order();
        order.markPaid();
        return order;
    }

    private Order expiredOrder() {
        Order order = order();
        order.expire(ORDERED_AT.plusMinutes(31));
        return order;
    }

    private Order cancelledOrderFromPending() {
        Order order = order();
        order.cancel(ORDERED_AT.plusMinutes(10));
        return order;
    }

    private OrderItem item(Long eventItemId, Long eventItemOptionId, String unitPrice, int quantity) {
        return OrderItem.create(
                eventItemId,
                eventItemOptionId,
                "색상",
                "Black",
                new BigDecimal(unitPrice),
                quantity);
    }
}
