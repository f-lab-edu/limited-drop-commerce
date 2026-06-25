package com.mist.commerce.domain.order.dto;

import com.mist.commerce.domain.order.service.CancelResult;
import java.time.LocalDateTime;

public record OrderCancelResponse(Long orderId, String status, LocalDateTime cancelledAt) {

    public static OrderCancelResponse from(CancelResult result) {
        return new OrderCancelResponse(result.orderId(), result.status(), result.cancelledAt());
    }
}
