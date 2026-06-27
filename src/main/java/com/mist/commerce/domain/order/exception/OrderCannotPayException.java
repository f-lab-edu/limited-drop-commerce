package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_CANNOT_PAY;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderCannotPayException extends BusinessException {

    public static final String CODE = "ORDER_CANNOT_PAY";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public OrderCannotPayException() {
        super(CODE, HTTP_STATUS, ORDER_CANNOT_PAY.getMessage(), ErrorDetail.empty());
    }
}
