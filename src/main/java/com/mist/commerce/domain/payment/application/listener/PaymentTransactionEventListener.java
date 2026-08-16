package com.mist.commerce.domain.payment.application.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentTransactionEventListener {

    private final PaymentEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(PaymentCompletedEvent event) {
        eventPublisher.publishPaymentCompleted(event);
    }
}
