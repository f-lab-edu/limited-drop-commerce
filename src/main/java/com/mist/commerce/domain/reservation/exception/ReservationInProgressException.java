package com.mist.commerce.domain.reservation.exception;

import static com.mist.commerce.domain.reservation.exception.ReservationExceptionMessage.RESERVATION_IN_PROGRESS;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class ReservationInProgressException extends BusinessException {

    public static final String CODE = "RESERVATION_IN_PROGRESS";
    public static final HttpStatus HTTP_STATUS = HttpStatus.CONFLICT;

    public ReservationInProgressException() {
        super(CODE, HTTP_STATUS, RESERVATION_IN_PROGRESS.getMessage(), ErrorDetail.empty());
    }
}
