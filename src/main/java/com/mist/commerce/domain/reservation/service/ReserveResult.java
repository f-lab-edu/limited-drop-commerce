package com.mist.commerce.domain.reservation.service;

import java.time.LocalDateTime;

public record ReserveResult(Long orderId, LocalDateTime expiresAt, String status) {
}
