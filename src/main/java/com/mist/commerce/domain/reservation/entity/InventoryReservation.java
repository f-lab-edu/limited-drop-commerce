package com.mist.commerce.domain.reservation.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "event_item_id")
    private Long eventItemId;

    @Column(name = "event_item_option_id")
    private Long eventItemOptionId;

    @Column(name = "reserved_quantity")
    private int reservedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ReservationStatus status;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    private InventoryReservation(
            Long orderId,
            Long eventItemId,
            Long eventItemOptionId,
            int reservedQuantity,
            LocalDateTime reservedAt,
            Duration ttl
    ) {
        validate(reservedQuantity);
        this.orderId = orderId;
        this.eventItemId = eventItemId;
        this.eventItemOptionId = eventItemOptionId;
        this.reservedQuantity = reservedQuantity;
        this.status = ReservationStatus.RESERVED;
        this.reservedAt = reservedAt;
        this.expiresAt = reservedAt.plus(ttl);
        this.confirmedAt = null;
        this.releasedAt = null;
    }

    public static InventoryReservation create(
            Long orderId,
            Long eventItemId,
            Long eventItemOptionId,
            int reservedQuantity,
            LocalDateTime reservedAt,
            Duration ttl
    ) {
        return new InventoryReservation(orderId, eventItemId, eventItemOptionId, reservedQuantity, reservedAt, ttl);
    }

    public void confirm(LocalDateTime confirmedAt) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved inventory reservations can be confirmed");
        }

        status = ReservationStatus.CONFIRMED;
        this.confirmedAt = confirmedAt;
    }

    public void expire() {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Only reserved inventory reservations can expire");
        }

        status = ReservationStatus.EXPIRED;
    }

    public void release(LocalDateTime releasedAt) {
        if (status != ReservationStatus.RESERVED && status != ReservationStatus.EXPIRED) {
            throw new IllegalStateException("Only reserved or expired inventory reservations can be released");
        }

        status = ReservationStatus.RELEASED;
        this.releasedAt = releasedAt;
    }

    private static void validate(int reservedQuantity) {
        if (reservedQuantity <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be positive");
        }
    }
}
