package com.mist.commerce.domain.payment.exception;

import static com.mist.commerce.domain.payment.exception.PaymentExceptionMessage.PAYMENT_AMOUNT_MISMATCH;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class PaymentAmountMismatchException extends BusinessException {

    public static final String CODE = "PAYMENT_AMOUNT_MISMATCH";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public PaymentAmountMismatchException() {
        super(CODE, HTTP_STATUS, PAYMENT_AMOUNT_MISMATCH.getMessage(), ErrorDetail.empty());
    }
}
