package com.mist.commerce.domain.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends RuntimeException {

    public static final String CODE = "INVALID_TOKEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidTokenException() {
        super("Invalid token");
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
