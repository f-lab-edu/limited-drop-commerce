package com.mist.commerce.domain.reservation.dto;

import com.mist.commerce.domain.reservation.service.ReserveResult;
import java.time.LocalDateTime;

public record ReservationResponse(Long orderId, LocalDateTime expiresAt, String status) {

    public static ReservationResponse from(ReserveResult result) {
        return new ReservationResponse(result.orderId(), result.expiresAt(), result.status());
    }
}
