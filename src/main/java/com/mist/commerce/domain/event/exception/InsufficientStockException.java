package com.mist.commerce.domain.event.exception;

import static com.mist.commerce.domain.event.exception.EventExceptionMessage.INSUFFICIENT_STOCK;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {

    public static final String CODE = "INSUFFICIENT_STOCK";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public InsufficientStockException() {
        super(CODE, HTTP_STATUS, INSUFFICIENT_STOCK.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
