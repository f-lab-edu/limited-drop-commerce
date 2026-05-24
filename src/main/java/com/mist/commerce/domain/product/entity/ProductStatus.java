package com.mist.commerce.domain.product.entity;

public enum ProductStatus {
    READY,
    ON_SALE,
    SOLD_OUT,
    STOPPED;

    public boolean isCreatable() {
        return this == READY;
    }
}
