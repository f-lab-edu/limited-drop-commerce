package com.mist.commerce.domain.brand.exception;

import org.springframework.http.HttpStatus;

public class BrandNameDuplicatedException extends RuntimeException {

    public static final String CODE = "BRAND_NAME_DUPLICATED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public BrandNameDuplicatedException(Long companyId, String name) {
        super("Duplicated brand name '" + name + "' for company: " + companyId);
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
