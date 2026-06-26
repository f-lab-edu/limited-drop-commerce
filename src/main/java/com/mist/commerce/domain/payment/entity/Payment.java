package com.mist.commerce.domain.payment.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "payment_no", unique = true)
    private String paymentNo;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    private Payment(
            String paymentNo,
            Long userId,
            Long orderId,
            BigDecimal amount,
            LocalDateTime requestedAt,
            String idempotencyKey
    ) {
        this.paymentNo = paymentNo;
        this.userId = userId;
        this.orderId = orderId;
        this.status = PaymentStatus.REQUESTED;
        this.amount = amount;
        this.requestedAt = requestedAt;
        this.approvedAt = null;
        this.failedAt = null;
        this.canceledAt = null;
        this.idempotencyKey = idempotencyKey;
    }

    public static Payment create(
            String paymentNo,
            Long userId,
            Long orderId,
            BigDecimal amount,
            LocalDateTime requestedAt,
            String idempotencyKey
    ) {
        return new Payment(paymentNo, userId, orderId, amount, requestedAt, idempotencyKey);
    }

    public void approve(LocalDateTime approvedAt) {
        if (status != PaymentStatus.REQUESTED) {
            throw new IllegalStateException("Only requested payments can be approved");
        }

        status = PaymentStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    public void fail(LocalDateTime failedAt) {
        if (status != PaymentStatus.REQUESTED) {
            throw new IllegalStateException("Only requested payments can fail");
        }

        status = PaymentStatus.FAILED;
        this.failedAt = failedAt;
    }
}
