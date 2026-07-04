package com.mist.commerce.domain.reservation.repository;

public interface OptionStockStore {

    void initialize(Long optionStockId, int availableQuantity);

    long tryDecrease(Long optionStockId, int quantity);

    long tryDecrease(Long optionStockId, int quantity, int fallbackAvailable);

    void increase(Long optionStockId, int quantity);

    Long getRemaining(Long optionStockId);
}

