package com.mist.commerce.domain.reservation.application.listener;

public record OptionStockReservedEvent(
        Long optionStockId,
        int quantity
) {
}
