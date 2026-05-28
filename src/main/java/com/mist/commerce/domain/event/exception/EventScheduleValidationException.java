package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.EVENT_SCHEDULE_VALIDATION_ERROR;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class EventScheduleValidationException extends BusinessException {

    public static final String CODE = "EVENT_SCHEDULE_VALIDATION_ERROR";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public EventScheduleValidationException() {
        super(CODE, HTTP_STATUS, EVENT_SCHEDULE_VALIDATION_ERROR.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
