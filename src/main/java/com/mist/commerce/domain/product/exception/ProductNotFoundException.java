package com.mist.commerce.domain.product.exception;

import org.springframework.http.HttpStatus;

public class ProductNotFoundException extends RuntimeException {

    public static final String CODE = "PRODUCT_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.NOT_FOUND;

    private final String code = CODE;

    public ProductNotFoundException(Long productId) {
        super("상품을 찾을 수 없습니다. (id: " + productId + ")");
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
