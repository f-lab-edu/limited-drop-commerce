package com.mist.commerce.domain.payment.event;

public interface PaymentEventPublisher {

    void publishPaymentCompleted(PaymentCompletedEvent event);
}
