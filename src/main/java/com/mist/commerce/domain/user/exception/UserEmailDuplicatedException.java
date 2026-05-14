package com.mist.commerce.domain.user.exception;

import org.springframework.http.HttpStatus;

public class UserEmailDuplicatedException extends RuntimeException {

    public static final String CODE = "USER_EMAIL_DUPLICATED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public UserEmailDuplicatedException(String email) {
        super("Duplicated user email: " + email);
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
