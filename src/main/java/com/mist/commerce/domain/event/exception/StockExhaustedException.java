package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.STOCK_EXHAUSTED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class StockExhaustedException extends BusinessException {

    public static final String CODE = "STOCK_EXHAUSTED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public StockExhaustedException() {
        super(CODE, HTTP_STATUS, STOCK_EXHAUSTED.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
