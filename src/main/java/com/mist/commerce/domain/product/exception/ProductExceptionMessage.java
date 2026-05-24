package com.mist.commerce.domain.product.exception;

public enum ProductExceptionMessage {
    NOT_FOUND("상품을 찾을 수 없습니다.");

    private final String message;

    ProductExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
