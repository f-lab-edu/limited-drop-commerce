package com.mist.commerce.domain.order.service;

import java.time.LocalDateTime;

public record CancelResult(Long orderId, String status, LocalDateTime cancelledAt) {
}
