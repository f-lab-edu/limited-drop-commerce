package com.mist.commerce.domain.reservation.service;

public record ReserveCommand(
        Long userId,
        Long eventId,
        Long eventItemId,
        Long eventItemOptionStockId,
        int quantity,
        String idempotencyKey
) {

    public ReserveCommand(
            Long userId,
            Long eventId,
            Long eventItemId,
            Long eventItemOptionStockId,
            int quantity
    ) {
        this(userId, eventId, eventItemId, eventItemOptionStockId, quantity, null);
    }
}
