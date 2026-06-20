package com.mist.commerce.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
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

    private Order order(Long userId, Long eventId) {
        return Order.create(
                userId,
                eventId,
                List.of(item(1L, 11L, "150000", 1)),
                ORDERED_AT,
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
}
