package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.INVALID_RESERVATION_QUANTITY;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class InvalidReservationQuantityException extends BusinessException {

    public static final String CODE = "INVALID_RESERVATION_QUANTITY";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public InvalidReservationQuantityException() {
        super(CODE, HTTP_STATUS, INVALID_RESERVATION_QUANTITY.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
