package com.mist.commerce.domain.reservation.application.service;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.InsufficientStockException;
import com.mist.commerce.domain.event.exception.StockExhaustedException;
import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final OptionStockStore optionStockStore;

    public void reserve(EventItemOptionStock optionStock, int quantity) {
        int dbAvailable = optionStock.getStockQuantity() - optionStock.getReservedQuantity();

        long remaining = optionStockStore.tryDecrease(
                optionStock.getId(),
                quantity,
                dbAvailable
        );

        if (remaining < 0) {
            throw dbAvailable <= 0 ? new StockExhaustedException() : new InsufficientStockException();
        }

        optionStock.reserve(quantity);
    }
}
