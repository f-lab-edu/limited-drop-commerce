package com.mist.commerce.domain.event.exception;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class EventScheduleValidationException extends BusinessException {

    public static final String CODE = "VALIDATION_ERROR";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public EventScheduleValidationException(String message) {
        super(CODE, HTTP_STATUS, message, ErrorDetail.of("", "", null));
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
