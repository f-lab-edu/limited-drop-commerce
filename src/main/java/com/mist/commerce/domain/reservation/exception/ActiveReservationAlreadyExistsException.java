package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.ACTIVE_RESERVATION_ALREADY_EXISTS;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ActiveReservationAlreadyExistsException extends BusinessException {

    public static final String CODE = "ACTIVE_RESERVATION_ALREADY_EXISTS";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public ActiveReservationAlreadyExistsException() {
        super(CODE, HTTP_STATUS, ACTIVE_RESERVATION_ALREADY_EXISTS.getMessage(), ErrorDetail.empty());
    }

    public String getCode() {
        return CODE;
    }

    public HttpStatus getHttpStatus() {
        return HTTP_STATUS;
    }
}
