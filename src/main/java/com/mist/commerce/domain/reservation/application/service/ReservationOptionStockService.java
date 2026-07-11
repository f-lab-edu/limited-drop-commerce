package com.mist.commerce.domain.reservation.application.service;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.InsufficientStockException;
import com.mist.commerce.domain.event.exception.StockExhaustedException;
import com.mist.commerce.domain.reservation.application.listener.OptionStockReservedEvent;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationOptionStockService {
    private final ApplicationEventPublisher eventPublisher;
    private final OptionStockStore optionStockStore;

    public void reserve(EventItemOptionStock optionStock, ReserveCommand command) {
        int dbAvailable = optionStock.getStockQuantity() - optionStock.getReservedQuantity();

        long remaining = optionStockStore.tryDecrease(
                optionStock.getId(),
                command.quantity(),
                dbAvailable
        );

        if (remaining < 0) {
            throw dbAvailable <= 0 ? new StockExhaustedException() : new InsufficientStockException();
        }

        optionStock.reserve(command.quantity());
        eventPublisher.publishEvent(new OptionStockReservedEvent(
                optionStock.getId(),
                command.quantity()
        ));
    }
}
