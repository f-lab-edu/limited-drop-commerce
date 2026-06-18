package com.mist.commerce.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventoryReservationTest {

    private static final Long ORDER_ID = 100L;
    private static final Long EVENT_ITEM_ID = 200L;
    private static final Long EVENT_ITEM_OPTION_ID = 300L;
    private static final LocalDateTime RESERVED_AT = LocalDateTime.of(2026, 6, 18, 12, 0);
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

    @Test
    @DisplayName("TC-INV-RES-001: ReservationStatus가 선점 도메인 상태값을 정확히 제공한다")
    void reservationStatus_containsDefinedDomainStatusesOnly() {
        assertThat(Arrays.asList(ReservationStatus.values()))
                .containsExactlyInAnyOrder(
                        ReservationStatus.RESERVED,
                        ReservationStatus.CONFIRMED,
                        ReservationStatus.RELEASED,
                        ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("TC-INV-RES-002: 선점 이력 생성 시 RESERVED 상태와 만료 시각이 설정된다")
    void create_setsReservedStatusAndExpirationMetadata() {
        InventoryReservation reservation = reservation();

        assertThat(reservation.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(reservation.getEventItemId()).isEqualTo(EVENT_ITEM_ID);
        assertThat(reservation.getEventItemOptionId()).isEqualTo(EVENT_ITEM_OPTION_ID);
        assertThat(reservation.getReservedQuantity()).isEqualTo(2);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getReservedAt()).isEqualTo(RESERVED_AT);
        assertThat(reservation.getExpiresAt()).isEqualTo(RESERVED_AT.plus(PAYMENT_TTL));
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getReleasedAt()).isNull();
    }

    @Test
    @DisplayName("TC-INV-RES-003: 선점 수량이 0이면 선점 이력 생성에 실패한다")
    void create_whenReservedQuantityIsZero_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> InventoryReservation.create(
                ORDER_ID,
                EVENT_ITEM_ID,
                EVENT_ITEM_OPTION_ID,
                0,
                RESERVED_AT,
                PAYMENT_TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-INV-RES-004: 선점 수량이 음수이면 선점 이력 생성에 실패한다")
    void create_whenReservedQuantityIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> InventoryReservation.create(
                ORDER_ID,
                EVENT_ITEM_ID,
                EVENT_ITEM_OPTION_ID,
                -1,
                RESERVED_AT,
                PAYMENT_TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-INV-RES-005: RESERVED 상태를 결제 확정하면 CONFIRMED 상태가 되고 confirmedAt이 기록된다")
    void confirm_whenReserved_changesStatusToConfirmedAndRecordsConfirmedAt() {
        InventoryReservation reservation = reservation();
        LocalDateTime confirmedAt = RESERVED_AT.plusMinutes(5);

        reservation.confirm(confirmedAt);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(RESERVED_AT.plus(PAYMENT_TTL));
    }

    @Test
    @DisplayName("TC-INV-RES-006: RESERVED 상태를 만료 처리하면 EXPIRED 상태가 된다")
    void expire_whenReserved_changesStatusToExpiredAndKeepsExpiresAt() {
        InventoryReservation reservation = reservation();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        reservation.expire();

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getReleasedAt()).isNull();
    }

    @Test
    @DisplayName("TC-INV-RES-007: RESERVED 상태를 취소 복구하면 RELEASED 상태가 되고 releasedAt이 기록된다")
    void release_whenReserved_changesStatusToReleasedAndRecordsReleasedAt() {
        InventoryReservation reservation = reservation();
        LocalDateTime releasedAt = RESERVED_AT.plusMinutes(10);

        reservation.release(releasedAt);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isEqualTo(releasedAt);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(RESERVED_AT.plus(PAYMENT_TTL));
    }

    @Test
    @DisplayName("TC-INV-RES-008: EXPIRED 상태를 만료 복구하면 RELEASED 상태가 되고 releasedAt이 기록된다")
    void release_whenExpired_changesStatusToReleasedAndRecordsReleasedAt() {
        InventoryReservation reservation = expiredReservation();
        LocalDateTime releasedAt = RESERVED_AT.plusMinutes(35);
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        reservation.release(releasedAt);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isEqualTo(releasedAt);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-009: CONFIRMED 상태는 복구할 수 없다")
    void release_whenConfirmed_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = confirmedReservation();
        LocalDateTime originalConfirmedAt = reservation.getConfirmedAt();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(() -> reservation.release(RESERVED_AT.plusMinutes(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(originalConfirmedAt);
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-010: CONFIRMED 상태는 만료 처리할 수 없다")
    void expire_whenConfirmed_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = confirmedReservation();
        LocalDateTime originalConfirmedAt = reservation.getConfirmedAt();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(reservation::expire)
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(originalConfirmedAt);
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-011: RELEASED 상태는 결제 확정할 수 없다")
    void confirm_whenReleased_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = releasedReservation();
        LocalDateTime originalReleasedAt = reservation.getReleasedAt();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(() -> reservation.confirm(RESERVED_AT.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isEqualTo(originalReleasedAt);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-012: RELEASED 상태는 다시 복구할 수 없다")
    void release_whenAlreadyReleased_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = releasedReservation();
        LocalDateTime originalReleasedAt = reservation.getReleasedAt();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(() -> reservation.release(RESERVED_AT.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(reservation.getReleasedAt()).isEqualTo(originalReleasedAt);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-013: EXPIRED 상태는 결제 확정할 수 없다")
    void confirm_whenExpired_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = expiredReservation();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(() -> reservation.confirm(RESERVED_AT.plusMinutes(35)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-014: EXPIRED 상태는 다시 만료 처리할 수 없다")
    void expire_whenAlreadyExpired_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = expiredReservation();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(reservation::expire)
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservation.getConfirmedAt()).isNull();
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    @DisplayName("TC-INV-RES-015: CONFIRMED 상태는 다시 결제 확정할 수 없다")
    void confirm_whenAlreadyConfirmed_throwsIllegalStateExceptionAndKeepsState() {
        InventoryReservation reservation = confirmedReservation();
        LocalDateTime originalConfirmedAt = reservation.getConfirmedAt();
        LocalDateTime originalExpiresAt = reservation.getExpiresAt();

        assertThatThrownBy(() -> reservation.confirm(RESERVED_AT.plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getConfirmedAt()).isEqualTo(originalConfirmedAt);
        assertThat(reservation.getReleasedAt()).isNull();
        assertThat(reservation.getExpiresAt()).isEqualTo(originalExpiresAt);
    }

    private InventoryReservation reservation() {
        return InventoryReservation.create(
                ORDER_ID,
                EVENT_ITEM_ID,
                EVENT_ITEM_OPTION_ID,
                2,
                RESERVED_AT,
                PAYMENT_TTL);
    }

    private InventoryReservation confirmedReservation() {
        InventoryReservation reservation = reservation();
        reservation.confirm(RESERVED_AT.plusMinutes(5));
        return reservation;
    }

    private InventoryReservation expiredReservation() {
        InventoryReservation reservation = reservation();
        reservation.expire();
        return reservation;
    }

    private InventoryReservation releasedReservation() {
        InventoryReservation reservation = reservation();
        reservation.release(RESERVED_AT.plusMinutes(10));
        return reservation;
    }
}
