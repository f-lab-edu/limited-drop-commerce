package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_CANCEL_TEMPORARILY_UNAVAILABLE;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderCancelTemporarilyUnavailableException extends BusinessException {

    public static final String CODE = "ORDER_CANCEL_TEMPORARILY_UNAVAILABLE";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public OrderCancelTemporarilyUnavailableException() {
        super(CODE, HTTP_STATUS, ORDER_CANCEL_TEMPORARILY_UNAVAILABLE.getMessage(), ErrorDetail.empty());
    }
}
