package com.mist.commerce.domain.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(
        @NotNull Long eventId,
        @NotNull Long eventItemId,
        @NotNull Long eventItemOptionStockId,
        @Min(1) int quantity
) {
}
