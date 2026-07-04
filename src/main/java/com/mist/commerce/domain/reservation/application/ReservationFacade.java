package com.mist.commerce.domain.reservation.application;

import com.mist.commerce.common.idempotency.ClaimResult;
import com.mist.commerce.common.idempotency.IdempotencyClaimResolver;
import com.mist.commerce.common.idempotency.IdempotencyStore;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.reservation.application.listener.OptionStockReservedEvent;
import com.mist.commerce.domain.reservation.application.listener.ReservationCreatedAfterCommitEvent;
import com.mist.commerce.domain.reservation.application.listener.ReservationIdempotencyClaimedEvent;
import com.mist.commerce.domain.reservation.application.service.ReservationService;
import com.mist.commerce.domain.reservation.application.support.ReservationCreator;
import com.mist.commerce.domain.reservation.application.support.ReservationValidator;
import com.mist.commerce.domain.reservation.application.support.ReserveContextLoader;
import com.mist.commerce.domain.reservation.application.support.ReserveFingerprintGenerator;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ReservationFacade {

    private final ApplicationEventPublisher eventPublisher;
    private final IdempotencyStore idempotencyStore;
    private final IdempotencyClaimResolver idempotencyClaimResolver;
    private final ReserveContextLoader contextLoader;
    private final ReservationValidator validator;
    private final ReservationService reserveService;
    private final ReservationCreator reservationCreator;
    private final ReserveFingerprintGenerator fingerprintGenerator;

    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        String fingerprint = fingerprintGenerator.generate(command);
        ClaimResult claimResult = idempotencyStore.claim(command.userId(), command.idempotencyKey(), fingerprint,
                ReservePolicy.PAYMENT_TTL);

        Optional<ReserveResult> resolved = idempotencyClaimResolver.resolve(claimResult, ReserveResult.class);
        if (resolved.isPresent()) {
            return resolved.get();
        }

        eventPublisher.publishEvent(new ReservationIdempotencyClaimedEvent(
                command.userId(),
                command.idempotencyKey(),
                fingerprint));

        ReserveContext context = contextLoader.load(command);
        validator.validate(command, context);

        EventItemOptionStock optionStock = context.eventItemOptionStock();
        reserveService.reserve(optionStock, command.quantity());
        eventPublisher.publishEvent(new OptionStockReservedEvent(
                optionStock.getId(),
                command.quantity()
        ));

        ReserveResult result = reservationCreator.create(command, context);
        eventPublisher.publishEvent(new ReservationCreatedAfterCommitEvent(
                command.userId(),
                command.idempotencyKey(),
                fingerprint,
                result
        ));
        return result;
    }
}
