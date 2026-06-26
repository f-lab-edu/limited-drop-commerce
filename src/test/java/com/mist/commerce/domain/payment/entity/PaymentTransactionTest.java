package com.mist.commerce.domain.payment.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentTransactionTest {

    private static final Long PAYMENT_ID = 1L;
    private static final TransactionType TRANSACTION_TYPE = TransactionType.APPROVE;
    private static final String EXTERNAL_TRANSACTION_ID = "toss-tx-001";
    private static final String REQUEST_PAYLOAD = "{\"paymentKey\":\"" + "request".repeat(600) + "\"}";
    private static final String RESPONSE_PAYLOAD = "{\"status\":\"DONE\",\"raw\":\"" + "response".repeat(600) + "\"}";

    @Test
    @DisplayName("TC-PAY-TX-001: 트랜잭션 생성 시 PG 이력 필드를 보존한다")
    void create_preservesPaymentTransactionFields() {
        PaymentTransaction transaction = PaymentTransaction.create(
                PAYMENT_ID,
                TRANSACTION_TYPE,
                EXTERNAL_TRANSACTION_ID,
                REQUEST_PAYLOAD,
                RESPONSE_PAYLOAD,
                TransactionStatus.SUCCESS);

        assertThat(transaction.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(transaction.getTransactionType()).isEqualTo(TRANSACTION_TYPE);
        assertThat(transaction.getExternalTransactionId()).isEqualTo(EXTERNAL_TRANSACTION_ID);
        assertThat(transaction.getRequestPayload()).isEqualTo(REQUEST_PAYLOAD);
        assertThat(transaction.getResponsePayload()).isEqualTo(RESPONSE_PAYLOAD);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    @DisplayName("TC-PAY-ENUM-001: TransactionType이 확정된 ERD 기준 상태값을 모두 제공한다")
    void transactionType_containsDefinedDomainTypesOnly() {
        assertThat(Arrays.asList(TransactionType.values()))
                .containsExactlyInAnyOrder(
                        TransactionType.REQUEST,
                        TransactionType.APPROVE,
                        TransactionType.FAIL,
                        TransactionType.CANCEL);
    }

    @Test
    @DisplayName("TC-PAY-ENUM-002: TransactionStatus가 확정된 ERD 기준 처리 상태값을 모두 제공한다")
    void transactionStatus_containsDefinedDomainStatusesOnly() {
        assertThat(Arrays.asList(TransactionStatus.values()))
                .containsExactlyInAnyOrder(
                        TransactionStatus.SUCCESS,
                        TransactionStatus.FAIL,
                        TransactionStatus.TIMEOUT,
                        TransactionStatus.ERROR);
    }
}
