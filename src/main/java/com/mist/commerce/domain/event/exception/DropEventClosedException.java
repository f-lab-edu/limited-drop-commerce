package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.DROP_EVENT_CLOSED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class DropEventClosedException extends BusinessException {

    public static final String CODE = "DROP_EVENT_CLOSED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public DropEventClosedException() {
        super(CODE, HTTP_STATUS, DROP_EVENT_CLOSED.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
