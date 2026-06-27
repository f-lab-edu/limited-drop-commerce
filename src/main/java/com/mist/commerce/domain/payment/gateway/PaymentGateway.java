package com.mist.commerce.domain.payment.gateway;

public interface PaymentGateway {

    PaymentApproval approve(PaymentApprovalCommand command);
}
