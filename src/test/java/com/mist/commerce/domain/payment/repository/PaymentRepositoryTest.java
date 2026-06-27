package com.mist.commerce.domain.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.payment.entity.Payment;
import com.mist.commerce.domain.payment.entity.PaymentStatus;
import com.mist.commerce.domain.payment.entity.PaymentTransaction;
import com.mist.commerce.domain.payment.entity.TransactionStatus;
import com.mist.commerce.domain.payment.entity.TransactionType;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class PaymentRepositoryTest extends MySqlContainerTestSupport {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 6, 27, 12, 0);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("TC-PAY-REP-001: payment_no unique 제약 위반 시 예외가 발생한다")
    void saveAndFlush_whenPaymentNoDuplicated_throwsDataIntegrityViolationException() {
        Payment first = payment("PAY-DUP-001", "idem-pay-dup-payment-no-001");
        paymentRepository.saveAndFlush(first);
        clearPersistenceContext();
        Payment duplicate = payment("PAY-DUP-001", "idem-pay-dup-payment-no-002");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();
        assertThat(paymentRepository.findById(first.getId())).isPresent();
    }

    @Test
    @DisplayName("TC-PAY-REP-002: idempotency_key로 결제를 조회할 수 있다")
    void findByIdempotencyKey_whenMatchingPaymentExists_returnsPayment() {
        Payment saved = paymentRepository.saveAndFlush(payment("PAY-FIND-001", "idem-pay-find-001"));
        clearPersistenceContext();

        Payment found = paymentRepository.findByIdempotencyKey("idem-pay-find-001").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getPaymentNo()).isEqualTo("PAY-FIND-001");
        assertThat(found.getUserId()).isEqualTo(USER_ID);
        assertThat(found.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(found.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(found.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }

    @Test
    @DisplayName("TC-PAY-REP-003: idempotency_key unique 제약 위반 시 예외가 발생한다")
    void saveAndFlush_whenIdempotencyKeyDuplicated_throwsDataIntegrityViolationException() {
        Payment first = payment("PAY-DUP-IDEM-001", "idem-pay-dup-001");
        paymentRepository.saveAndFlush(first);
        clearPersistenceContext();
        Payment duplicate = payment("PAY-DUP-IDEM-002", "idem-pay-dup-001");

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
        entityManager.clear();
        assertThat(paymentRepository.findById(first.getId())).isPresent();
    }

    @Test
    @DisplayName("TC-PAY-REP-004: PaymentTransaction을 영속하고 다시 조회할 수 있다")
    void persistPaymentTransaction_whenAssociatedPaymentExists_canReloadById() {
        Payment savedPayment = paymentRepository.saveAndFlush(payment("PAY-TX-001", "idem-pay-tx-001"));
        PaymentTransaction transaction = PaymentTransaction.create(
                savedPayment.getId(),
                TransactionType.APPROVE,
                "toss-tx-001",
                "{\"paymentKey\":\"request\"}",
                "{\"status\":\"DONE\"}",
                TransactionStatus.SUCCESS);

        PaymentTransaction savedTransaction = paymentTransactionRepository.saveAndFlush(transaction);
        Long transactionId = savedTransaction.getId();
        clearPersistenceContext();

        PaymentTransaction found = paymentTransactionRepository.findById(transactionId).orElseThrow();

        assertThat(found.getPaymentId()).isEqualTo(savedPayment.getId());
        assertThat(found.getTransactionType()).isEqualTo(TransactionType.APPROVE);
        assertThat(found.getExternalTransactionId()).isEqualTo("toss-tx-001");
        assertThat(found.getRequestPayload()).isEqualTo("{\"paymentKey\":\"request\"}");
        assertThat(found.getResponsePayload()).isEqualTo("{\"status\":\"DONE\"}");
        assertThat(found.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    private Payment payment(String paymentNo, String idempotencyKey) {
        return Payment.create(
                paymentNo,
                USER_ID,
                ORDER_ID,
                AMOUNT,
                REQUESTED_AT,
                idempotencyKey);
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
