package com.mist.commerce.domain.user.exception;

import org.springframework.http.HttpStatus;

public class OAuthAccountAlreadyLinkedToBusinessException extends RuntimeException {

    public static final String CODE = "OAUTH_ACCOUNT_ALREADY_LINKED_TO_BUSINESS";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public OAuthAccountAlreadyLinkedToBusinessException() {
        super("OAuth account is already linked to a business account");
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
