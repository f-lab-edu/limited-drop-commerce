package com.mist.commerce.domain.user.exception;

import com.mist.commerce.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidTokenException extends BusinessException {

    public static final String CODE = "INVALID_TOKEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.UNAUTHORIZED;

    public InvalidTokenException(String message) {
        super(CODE, HTTP_STATUS, message);
    }

    public InvalidTokenException(Throwable e) {
        super(CODE, HTTP_STATUS, "Invalid token", e);
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
