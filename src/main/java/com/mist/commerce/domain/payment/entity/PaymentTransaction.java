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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "payment_id")
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;

    @Column(name = "external_transaction_id")
    private String externalTransactionId;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status")
    private TransactionStatus transactionStatus;

    private PaymentTransaction(
            Long paymentId,
            TransactionType transactionType,
            String externalTransactionId,
            String requestPayload,
            String responsePayload,
            TransactionStatus transactionStatus
    ) {
        this.paymentId = paymentId;
        this.transactionType = transactionType;
        this.externalTransactionId = externalTransactionId;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
        this.transactionStatus = transactionStatus;
    }

    public static PaymentTransaction create(
            Long paymentId,
            TransactionType transactionType,
            String externalTransactionId,
            String requestPayload,
            String responsePayload,
            TransactionStatus transactionStatus
    ) {
        return new PaymentTransaction(
                paymentId,
                transactionType,
                externalTransactionId,
                requestPayload,
                responsePayload,
                transactionStatus);
    }
}
