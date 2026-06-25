package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_CANNOT_CANCEL;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderCannotCancelException extends BusinessException {

    public static final String CODE = "ORDER_CANNOT_CANCEL";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public OrderCannotCancelException() {
        super(CODE, HTTP_STATUS, ORDER_CANNOT_CANCEL.getMessage(), ErrorDetail.empty());
    }
}
