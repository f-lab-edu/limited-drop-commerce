package com.mist.commerce.domain.payment.exception;

import static com.mist.commerce.domain.payment.exception.PaymentExceptionMessage.PAYMENT_FAILED;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.springframework.http.HttpStatus;

public class PaymentFailedException extends BusinessException {

    public static final String CODE = "PAYMENT_FAILED";
    public static final HttpStatus HTTP_STATUS = HttpStatus.BAD_REQUEST;

    public PaymentFailedException() {
        super(CODE, HTTP_STATUS, PAYMENT_FAILED.getMessage(), ErrorDetail.empty());
    }

    public PaymentFailedException(Throwable cause) {
        super(CODE, HTTP_STATUS, PAYMENT_FAILED.getMessage(), cause);
    }
}
