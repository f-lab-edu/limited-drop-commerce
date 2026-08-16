package com.mist.commerce.domain.payment.gateway;

import com.mist.commerce.domain.payment.application.listener.PaymentCompletedEvent;

public interface PaymentEventPublisher {
    void publishPaymentCompleted(PaymentCompletedEvent event);
}
