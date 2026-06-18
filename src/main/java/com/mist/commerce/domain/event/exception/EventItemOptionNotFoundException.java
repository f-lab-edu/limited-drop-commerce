package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.EVENT_ITEM_OPTION_NOT_FOUND;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class EventItemOptionNotFoundException extends BusinessException {

    public static final String CODE = "EVENT_ITEM_OPTION_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.NOT_FOUND;

    public EventItemOptionNotFoundException() {
        super(CODE, HTTP_STATUS, EVENT_ITEM_OPTION_NOT_FOUND.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
