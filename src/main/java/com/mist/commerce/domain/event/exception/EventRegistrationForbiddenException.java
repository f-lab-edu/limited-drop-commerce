package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.EVENT_REGISTRATION_FORBIDDEN;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class EventRegistrationForbiddenException extends BusinessException {

    public static final String CODE = "EVENT_REGISTRATION_FORBIDDEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public EventRegistrationForbiddenException(Long userId) {
        super(CODE, HTTP_STATUS, EVENT_REGISTRATION_FORBIDDEN.getMessage(), ErrorDetail.of("userId", userId, null));
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
