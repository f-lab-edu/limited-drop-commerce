package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_FORBIDDEN;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderForbiddenException extends BusinessException {

    public static final String CODE = "ORDER_FORBIDDEN";
    public static final HttpStatus HTTP_STATUS = HttpStatus.FORBIDDEN;

    public OrderForbiddenException() {
        super(CODE, HTTP_STATUS, ORDER_FORBIDDEN.getMessage(), ErrorDetail.empty());
    }
}
