package com.mist.commerce.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTest {

    private static final String PAYMENT_NO = "PAY-20260627-0001";
    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 6, 27, 12, 0);
    private static final String IDEMPOTENCY_KEY = "idem-pay-001";

    @Test
    @DisplayName("TC-PAY-ENT-001: 팩토리 생성 시 REQUESTED 상태와 결제 메타데이터를 보존한다")
    void create_setsRequestedStatusAndPaymentMetadata() {
        Payment payment = payment();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getRequestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(payment.getPaymentNo()).isEqualTo(PAYMENT_NO);
        assertThat(payment.getUserId()).isEqualTo(USER_ID);
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(payment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getFailedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-002: REQUESTED 결제를 승인하면 APPROVED 상태가 되고 approvedAt이 기록된다")
    void approve_whenRequested_changesStatusToApprovedAndRecordsApprovedAt() {
        Payment payment = payment();
        LocalDateTime approvedAt = REQUESTED_AT.plusMinutes(3);

        payment.approve(approvedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(payment.getFailedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-003: REQUESTED 결제를 실패 처리하면 FAILED 상태가 되고 failedAt이 기록된다")
    void fail_whenRequested_changesStatusToFailedAndRecordsFailedAt() {
        Payment payment = payment();
        LocalDateTime failedAt = REQUESTED_AT.plusMinutes(4);

        payment.fail(failedAt);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(failedAt);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-004: APPROVED 결제를 다시 승인하면 IllegalStateException이 발생하고 상태가 유지된다")
    void approve_whenAlreadyApproved_throwsIllegalStateExceptionAndKeepsState() {
        LocalDateTime originalApprovedAt = REQUESTED_AT.plusMinutes(3);
        Payment payment = approvedPayment(originalApprovedAt);

        assertThatThrownBy(() -> payment.approve(REQUESTED_AT.plusMinutes(5)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(originalApprovedAt);
        assertThat(payment.getFailedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-005: APPROVED 결제를 실패 처리하면 IllegalStateException이 발생하고 상태가 유지된다")
    void fail_whenApproved_throwsIllegalStateExceptionAndKeepsState() {
        LocalDateTime originalApprovedAt = REQUESTED_AT.plusMinutes(3);
        Payment payment = approvedPayment(originalApprovedAt);

        assertThatThrownBy(() -> payment.fail(REQUESTED_AT.plusMinutes(5)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isEqualTo(originalApprovedAt);
        assertThat(payment.getFailedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-006: FAILED 결제를 승인하면 IllegalStateException이 발생하고 상태가 유지된다")
    void approve_whenFailed_throwsIllegalStateExceptionAndKeepsState() {
        LocalDateTime originalFailedAt = REQUESTED_AT.plusMinutes(4);
        Payment payment = failedPayment(originalFailedAt);

        assertThatThrownBy(() -> payment.approve(REQUESTED_AT.plusMinutes(5)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(originalFailedAt);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    @DisplayName("TC-PAY-ENT-007: FAILED 결제를 다시 실패 처리하면 IllegalStateException이 발생하고 상태가 유지된다")
    void fail_whenAlreadyFailed_throwsIllegalStateExceptionAndKeepsState() {
        LocalDateTime originalFailedAt = REQUESTED_AT.plusMinutes(4);
        Payment payment = failedPayment(originalFailedAt);

        assertThatThrownBy(() -> payment.fail(REQUESTED_AT.plusMinutes(5)))
                .isExactlyInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(originalFailedAt);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCanceledAt()).isNull();
    }

    private Payment payment() {
        return Payment.create(
                PAYMENT_NO,
                USER_ID,
                ORDER_ID,
                AMOUNT,
                REQUESTED_AT,
                IDEMPOTENCY_KEY);
    }

    private Payment approvedPayment(LocalDateTime approvedAt) {
        Payment payment = payment();
        payment.approve(approvedAt);
        return payment;
    }

    private Payment failedPayment(LocalDateTime failedAt) {
        Payment payment = payment();
        payment.fail(failedAt);
        return payment;
    }
}
