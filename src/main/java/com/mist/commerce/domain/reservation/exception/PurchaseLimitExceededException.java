package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.PURCHASE_LIMIT_EXCEEDED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class PurchaseLimitExceededException extends BusinessException {

    public static final String CODE = "PURCHASE_LIMIT_EXCEEDED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public PurchaseLimitExceededException() {
        super(CODE, HTTP_STATUS, PURCHASE_LIMIT_EXCEEDED.getMessage(), ErrorDetail.empty());
    }
}
