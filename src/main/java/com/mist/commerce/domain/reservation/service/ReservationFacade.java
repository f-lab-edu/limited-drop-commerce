package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.common.idempotency.ClaimResult;
import com.mist.commerce.common.idempotency.IdempotencyClaimResolver;
import com.mist.commerce.common.idempotency.IdempotencyStore;
import com.mist.commerce.common.json.JsonSerializer;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import com.mist.commerce.domain.reservation.repository.ReservationExpiryStore;
import com.mist.commerce.domain.reservation.support.ReserveFingerprintGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final IdempotencyStore idempotencyStore;
    private final ReservationExpiryStore reservationExpiryStore;
    private final IdempotencyClaimResolver idempotencyClaimResolver;
    private final ReservationService reserveService;
    private final ReserveFingerprintGenerator fingerprintGenerator;
    private final JsonSerializer serializer;

    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        String fingerprint = fingerprintGenerator.generate(command);
        ClaimResult claimResult = idempotencyStore.claim(command.userId(), command.idempotencyKey(), fingerprint,
                ReservePolicy.PAYMENT_TTL);

        Optional<ReserveResult> resolved = idempotencyClaimResolver.resolve(claimResult, ReserveResult.class);

        if (resolved.isPresent()) {
            return resolved.get();
        }

        ReserveResult[] resultHolder = new ReserveResult[1];

        registerIdempotencySynchronization(
                command.userId(),
                command.idempotencyKey(),
                fingerprint,
                resultHolder
        );

        ReserveResult result = reserveService.reserve(command);

        resultHolder[0] = result;
        return result;
    }

    private void registerIdempotencySynchronization(
            Long userId,
            String idempotencyKey,
            String fingerprint,
            ReserveResult[] resultHolder
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ReserveResult result = resultHolder[0];
                if (result != null) {
                    idempotencyStore.complete(userId, idempotencyKey, fingerprint, serializer.serialize(result));
                    reservationExpiryStore.markExpiry(result.orderId(), ReservePolicy.PAYMENT_TTL);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    idempotencyStore.release(userId, idempotencyKey);
                }
            }
        });
    }
}
