package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.RESERVATION_TEMPORARILY_UNAVAILABLE;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ReservationTemporarilyUnavailableException extends BusinessException {

    public static final String CODE = "RESERVATION_TEMPORARILY_UNAVAILABLE";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public ReservationTemporarilyUnavailableException() {
        super(CODE, HTTP_STATUS, RESERVATION_TEMPORARILY_UNAVAILABLE.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
