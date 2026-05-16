package com.mist.commerce.domain.brand.exception;

import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

public class BrandNotFoundException extends RuntimeException {
    public static final String CODE = "BRAND_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public BrandNotFoundException(Long brandId) {
        super("Brand not found with id: " + brandId);
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
