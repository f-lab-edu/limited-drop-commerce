package com.mist.commerce.domain.order.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "event_id")
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_quantity")
    private int totalQuantity;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;

    private Order(Long userId, Long eventId, List<OrderItem> items, LocalDateTime orderedAt, Duration ttl) {
        this.orderNo = "ORD-" + UUID.randomUUID();
        this.userId = userId;
        this.eventId = eventId;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.totalAmount = calculateTotalAmount(items);
        this.totalQuantity = calculateTotalQuantity(items);
        this.expiresAt = orderedAt.plus(ttl);
        this.orderedAt = orderedAt;
        this.cancelledAt = null;
        this.expiredAt = null;
        this.items = items;
    }

    public static Order create(Long userId, Long eventId, List<OrderItem> items, LocalDateTime orderedAt, Duration ttl) {
        return new Order(userId, eventId, items, orderedAt, ttl);
    }

    public void markPaid() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending payment orders can be paid");
        }

        status = OrderStatus.PAID;
    }

    public void expire(LocalDateTime expiredAt) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending payment orders can expire");
        }

        status = OrderStatus.EXPIRED;
        this.expiredAt = expiredAt;
    }

    public void cancel(LocalDateTime cancelledAt) {
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PAID) {
            throw new IllegalStateException("Only pending payment or paid orders can be cancelled");
        }

        status = OrderStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    private static BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int calculateTotalQuantity(List<OrderItem> items) {
        return items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }
}
