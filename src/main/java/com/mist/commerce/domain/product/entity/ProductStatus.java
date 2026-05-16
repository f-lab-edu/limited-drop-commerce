package com.mist.commerce.domain.product.entity;

public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE;

    public boolean isCreatable() {
        return this == DRAFT || this == ACTIVE;
    }
}
