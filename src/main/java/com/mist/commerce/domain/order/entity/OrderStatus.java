package com.mist.commerce.domain.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    EXPIRED,
    CANCELLED
}
