package com.mist.commerce.domain.reservation.service;

public record ReserveCommand(
        Long userId,
        Long eventId,
        Long eventItemId,
        Long eventItemOptionStockId,
        int quantity
) {
}
