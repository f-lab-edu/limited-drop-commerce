package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.IDEMPOTENCY_KEY_REUSED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends BusinessException {

    public static final String CODE = "IDEMPOTENCY_KEY_REUSED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.UNPROCESSABLE_ENTITY;

    public IdempotencyKeyReusedException() {
        super(CODE, HTTP_STATUS, IDEMPOTENCY_KEY_REUSED.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
