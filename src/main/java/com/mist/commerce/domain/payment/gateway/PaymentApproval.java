package com.mist.commerce.domain.payment.gateway;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class PaymentApproval {

    private final boolean approved;
    private final String externalTransactionId;
    private final LocalDateTime approvedAt;
    private final String requestPayload;
    private final String responsePayload;

    public PaymentApproval(
            boolean approved,
            String externalTransactionId,
            LocalDateTime approvedAt,
            String requestPayload,
            String responsePayload
    ) {
        this.approved = approved;
        this.externalTransactionId = externalTransactionId;
        this.approvedAt = approvedAt;
        this.requestPayload = requestPayload;
        this.responsePayload = responsePayload;
    }

    public boolean isApproved() {
        return approved;
    }
}
