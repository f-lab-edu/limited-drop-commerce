package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final StockReservationService stockReservationService;
    private final ReserveContextLoader contextLoader;
    private final ReservationValidator validator;
    private final ReservationCreator reservationCreator;

    public ReserveResult reserve(ReserveCommand command) {
        ReserveContext context = contextLoader.load(command);
        validator.validate(command, context);

        EventItemOptionStock optionStock = context.eventItemOptionStock();
        stockReservationService.reserve(optionStock, command.quantity());

        return reservationCreator.create(command, context);
    }
}
