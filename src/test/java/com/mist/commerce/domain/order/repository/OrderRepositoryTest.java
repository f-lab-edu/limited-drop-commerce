package com.mist.commerce.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class OrderRepositoryTest extends MySqlContainerTestSupport {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 100L;
    private static final LocalDateTime ORDERED_AT = LocalDateTime.of(2026, 6, 17, 12, 0);
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("TC-ORR-001: 같은 userId, eventId, status 주문이 있으면 true를 반환한다")
    void existsByUserIdAndEventIdAndStatus_whenMatchingOrderExists_returnsTrue() {
        orderRepository.saveAndFlush(order(USER_ID, EVENT_ID));

        boolean exists = orderRepository.existsByUserIdAndEventIdAndStatus(
                USER_ID,
                EVENT_ID,
                OrderStatus.PENDING_PAYMENT);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("TC-ORR-002: eventId가 다르면 false를 반환한다")
    void existsByUserIdAndEventIdAndStatus_whenEventIdDiffers_returnsFalse() {
        orderRepository.saveAndFlush(order(USER_ID, EVENT_ID));

        boolean exists = orderRepository.existsByUserIdAndEventIdAndStatus(
                USER_ID,
                101L,
                OrderStatus.PENDING_PAYMENT);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("TC-ORR-003: userId가 다르면 false를 반환한다")
    void existsByUserIdAndEventIdAndStatus_whenUserIdDiffers_returnsFalse() {
        orderRepository.saveAndFlush(order(USER_ID, EVENT_ID));

        boolean exists = orderRepository.existsByUserIdAndEventIdAndStatus(
                11L,
                EVENT_ID,
                OrderStatus.PENDING_PAYMENT);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("TC-ORR-004: status가 다르면 false를 반환한다")
    void existsByUserIdAndEventIdAndStatus_whenStatusDiffers_returnsFalse() {
        orderRepository.saveAndFlush(order(USER_ID, EVENT_ID));

        boolean exists = orderRepository.existsByUserIdAndEventIdAndStatus(
                USER_ID,
                EVENT_ID,
                OrderStatus.PAID);

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("TC-ORDER-EXPIRE-001: PENDING_PAYMENT 주문을 한 번만 EXPIRED로 전이한다")
    void expireIfPending_whenPendingPaymentOrderExists_expiresOnlyOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 12, 31);
        Order saved = orderRepository.saveAndFlush(order(USER_ID, EVENT_ID));
        clearPersistenceContext();

        int firstAffectedRows = orderRepository.expireIfPending(saved.getId(), now);
        clearPersistenceContext();
        Order expired = orderRepository.findById(saved.getId()).orElseThrow();

        int secondAffectedRows = orderRepository.expireIfPending(saved.getId(), now);
        clearPersistenceContext();
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(firstAffectedRows).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(expired.getExpiredAt()).isEqualTo(now);
        assertThat(secondAffectedRows).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reloaded.getExpiredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("TC-ORDER-EXPIRE-002: 이미 PAID 또는 EXPIRED인 주문은 변경하지 않는다")
    void expireIfPending_whenOrderIsPaidOrExpired_doesNotChangeState() {
        LocalDateTime existingExpiredAt = LocalDateTime.of(2026, 6, 17, 12, 30);
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 12, 31);
        Order paid = order(USER_ID, EVENT_ID);
        paid.markPaid();
        Order expired = order(USER_ID, EVENT_ID);
        expired.expire(existingExpiredAt);
        orderRepository.saveAllAndFlush(List.of(paid, expired));
        clearPersistenceContext();

        int paidAffectedRows = orderRepository.expireIfPending(paid.getId(), now);
        int expiredAffectedRows = orderRepository.expireIfPending(expired.getId(), now);
        clearPersistenceContext();
        Order reloadedPaid = orderRepository.findById(paid.getId()).orElseThrow();
        Order reloadedExpired = orderRepository.findById(expired.getId()).orElseThrow();

        assertThat(paidAffectedRows).isZero();
        assertThat(reloadedPaid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloadedPaid.getExpiredAt()).isNull();
        assertThat(expiredAffectedRows).isZero();
        assertThat(reloadedExpired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reloadedExpired.getExpiredAt()).isEqualTo(existingExpiredAt);
    }

    @Test
    @DisplayName("TC-ORDER-EXPIRE-003: 만료된 PENDING_PAYMENT 주문만 expiresAt 오름차순으로 반환한다")
    void findExpiredPendingPaymentIds_filtersByStatusAndExpiresAtAndSortsAscending() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 12, 30);
        Order expiredPendingA = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(5));
        Order expiredPendingB = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(10));
        Order notExpiredPending = orderExpiringAt(USER_ID, EVENT_ID, now.plusMinutes(1));
        Order boundaryPending = orderExpiringAt(USER_ID, EVENT_ID, now);
        Order paidExpired = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(20));
        paidExpired.markPaid();
        Order alreadyExpired = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(30));
        alreadyExpired.expire(now.minusMinutes(1));
        orderRepository.saveAllAndFlush(List.of(
                expiredPendingA,
                expiredPendingB,
                notExpiredPending,
                boundaryPending,
                paidExpired,
                alreadyExpired));
        clearPersistenceContext();

        List<Long> ids = orderRepository.findExpiredPendingPaymentIds(now, PageRequest.of(0, 10));

        assertThat(ids).containsExactly(expiredPendingB.getId(), expiredPendingA.getId());
    }

    @Test
    @DisplayName("TC-ORDER-EXPIRE-004: Pageable 한도만큼만 반환한다")
    void findExpiredPendingPaymentIds_whenMoreRowsThanPageSize_returnsOnlyLimit() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 17, 12, 30);
        Order first = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(30));
        Order second = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(20));
        Order third = orderExpiringAt(USER_ID, EVENT_ID, now.minusMinutes(10));
        orderRepository.saveAllAndFlush(List.of(first, second, third));
        clearPersistenceContext();

        List<Long> ids = orderRepository.findExpiredPendingPaymentIds(now, PageRequest.of(0, 2));

        assertThat(ids).containsExactly(first.getId(), second.getId());
    }

    private Order order(Long userId, Long eventId) {
        return Order.create(
                userId,
                eventId,
                List.of(item(1L, 11L, "150000", 1)),
                ORDERED_AT,
                PAYMENT_TTL);
    }

    private Order orderExpiringAt(Long userId, Long eventId, LocalDateTime expiresAt) {
        return Order.create(
                userId,
                eventId,
                List.of(item(1L, 11L, "150000", 1)),
                expiresAt.minus(PAYMENT_TTL),
                PAYMENT_TTL);
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

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
