package com.mist.commerce.domain.order.service;

public record CancelCommand(Long userId, Long orderId, String idempotencyKey) {
}
