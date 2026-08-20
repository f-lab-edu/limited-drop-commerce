package com.mist.commerce.domain.reservation.application.service;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.reservation.application.listener.ReservationCreatedAfterCommitEvent;
import com.mist.commerce.domain.reservation.application.support.ReservationCreator;
import com.mist.commerce.domain.reservation.application.support.ReservationValidator;
import com.mist.commerce.domain.reservation.application.support.ReserveContextLoader;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ApplicationEventPublisher eventPublisher;
    private final ReserveContextLoader contextLoader;
    private final ReservationValidator validator;
    private final ReservationCreator reservationCreator;
    private final ReservationOptionStockService reservationOptionStockService;

    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        ReserveContext context = contextLoader.load(command);
        validator.validate(command, context);
        EventItemOptionStock optionStock = context.eventItemOptionStock();

        reservationOptionStockService.reserve(optionStock, command);

        ReserveResult result = reservationCreator.create(command, context);
        eventPublisher.publishEvent(new ReservationCreatedAfterCommitEvent(
                command.userId(),
                result
        ));

        return result;
    }
}
