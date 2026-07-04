package com.mist.commerce.domain.reservation.application.listener;

import com.mist.commerce.common.idempotency.IdempotencyStore;
import com.mist.commerce.common.json.JsonSerializer;
import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import com.mist.commerce.domain.reservation.repository.ReservationExpiryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReservationTransactionEventListener {

    private final IdempotencyStore idempotencyStore;
    private final ReservationExpiryStore reservationExpiryStore;
    private final OptionStockStore optionStockStore;
    private final JsonSerializer serializer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAfterCommit(ReservationCreatedAfterCommitEvent event) {
        ReserveResult result = event.result();

        idempotencyStore.complete(
                event.userId(),
                event.idempotencyKey(),
                event.fingerprint(),
                serializer.serialize(result)
        );

        reservationExpiryStore.markExpiry(
                result.orderId(),
                ReservePolicy.PAYMENT_TTL
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleAfterRollback(ReservationIdempotencyClaimedEvent event) {

        idempotencyStore.release(
                event.userId(),
                event.idempotencyKey()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void compensateStock(OptionStockReservedEvent event) {
        optionStockStore.increase(
                event.optionStockId(),
                event.quantity()
        );
    }
}
