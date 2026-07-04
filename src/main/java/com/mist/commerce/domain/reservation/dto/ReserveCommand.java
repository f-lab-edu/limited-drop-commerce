package com.mist.commerce.domain.reservation.dto;

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
