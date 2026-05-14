package com.mist.commerce.domain.brand.exception;

import org.springframework.http.HttpStatus;

public class BrandRegistrationForbiddenException extends RuntimeException {

    public static final String CODE = "BRAND_REGISTRATION_FORBIDDEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public BrandRegistrationForbiddenException(Long userId) {
        super(userId == null
                ? "Brand registration forbidden: user not found"
                : "Brand registration forbidden for user: " + userId);
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
