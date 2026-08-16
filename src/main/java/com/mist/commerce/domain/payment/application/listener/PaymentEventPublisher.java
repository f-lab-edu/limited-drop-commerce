package com.mist.commerce.domain.payment.application.listener;

public interface PaymentEventPublisher {
    void publishPaymentCompleted(PaymentCompletedEvent event);
}
