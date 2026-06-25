package com.mist.commerce.domain.order.exception;

import static com.mist.commerce.domain.order.exception.OrderExceptionMessage.ORDER_ALREADY_CANCELLED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class OrderAlreadyCancelledException extends BusinessException {

    public static final String CODE = "ORDER_ALREADY_CANCELLED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public OrderAlreadyCancelledException() {
        super(CODE, HTTP_STATUS, ORDER_ALREADY_CANCELLED.getMessage(), ErrorDetail.empty());
    }
}
