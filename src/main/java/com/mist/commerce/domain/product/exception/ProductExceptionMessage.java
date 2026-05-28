package com.mist.commerce.domain.product.exception;

public enum ProductExceptionMessage {
    NOT_FOUND("상품을 찾을 수 없습니다."),
    OPTION_GROUP_NAME_DUPLICATED("옵션 그룹명이 중복되었습니다."),
    OPTION_VALUE_DUPLICATED("옵션 값이 중복되었습니다."),
    OPTION_VALUE_REQUIRED("옵션 그룹에는 최소 1개의 옵션 값이 필요합니다.");

    private final String message;

    ProductExceptionMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
