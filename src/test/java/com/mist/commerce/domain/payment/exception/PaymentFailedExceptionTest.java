package com.mist.commerce.domain.payment.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.global.exception.BusinessException;
import com.mist.commerce.global.response.ErrorDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentFailedExceptionTest {

    @Test
    @DisplayName("TC-PAY-EXC-001: PaymentFailedException은 BusinessException 계약을 따른다")
    void paymentFailedException_exposesBusinessExceptionContract() {
        PaymentFailedException exception = new PaymentFailedException();

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getCode()).isEqualTo("PAYMENT_FAILED");
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(PaymentExceptionMessage.PAYMENT_FAILED.getMessage());
        assertThat(exception.getErrorDetail()).isEqualTo(ErrorDetail.empty());
    }
}
