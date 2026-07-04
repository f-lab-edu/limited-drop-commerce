package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.InsufficientStockException;
import com.mist.commerce.domain.event.exception.StockExhaustedException;
import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class StockReservationService {

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

        registerRedisCompensation(optionStock.getId(), quantity);

        optionStock.reserve(quantity);
    }

    private void registerRedisCompensation(Long optionStockId, int quantity) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    optionStockStore.increase(optionStockId, quantity);
                }
            }
        });
    }
}
