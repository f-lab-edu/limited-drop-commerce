package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_NOT_FOUND;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends BusinessException {

    public static final String CODE = "ORDER_NOT_FOUND";
    public static final HttpStatus HTTP_STATUS = HttpStatus.NOT_FOUND;

    public OrderNotFoundException() {
        super(CODE, HTTP_STATUS, ORDER_NOT_FOUND.getMessage(), ErrorDetail.empty());
    }
}
