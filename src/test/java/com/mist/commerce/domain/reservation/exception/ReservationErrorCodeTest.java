package com.mist.commerce.domain.reservation.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.global.exception.BusinessException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReservationErrorCodeTest {

    private static final Pattern SCREAMING_SNAKE_CASE = Pattern.compile("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");

    @Test
    @DisplayName("INVALID_RESERVATION_QUANTITY는 400 BAD_REQUEST를 노출한다")
    void invalidReservationQuantity_exposesCodeAndHttpStatus() {
        assertErrorCode(
                new InvalidReservationQuantityException(),
                "INVALID_RESERVATION_QUANTITY",
                HttpStatus.BAD_REQUEST
        );
    }

    @Test
    @DisplayName("PURCHASE_LIMIT_EXCEEDED는 409 CONFLICT를 노출한다")
    void purchaseLimitExceeded_exposesCodeAndHttpStatus() {
        assertErrorCode(new PurchaseLimitExceededException(), "PURCHASE_LIMIT_EXCEEDED", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("ACTIVE_RESERVATION_ALREADY_EXISTS는 409 CONFLICT를 노출한다")
    void activeReservationAlreadyExists_exposesCodeAndHttpStatus() {
        assertErrorCode(
                new ActiveReservationAlreadyExistsException(),
                "ACTIVE_RESERVATION_ALREADY_EXISTS",
                HttpStatus.CONFLICT
        );
    }

    @Test
    @DisplayName("RESERVATION_TEMPORARILY_UNAVAILABLE은 409 CONFLICT를 노출한다")
    void reservationTemporarilyUnavailable_exposesCodeAndHttpStatus() {
        assertErrorCode(
                new ReservationTemporarilyUnavailableException(),
                "RESERVATION_TEMPORARILY_UNAVAILABLE",
                HttpStatus.CONFLICT
        );
    }

    @Test
    @DisplayName("IDEMPOTENCY_KEY_REUSED는 422 UNPROCESSABLE_ENTITY를 노출한다")
    void idempotencyKeyReused_exposesCodeAndHttpStatus() {
        assertErrorCode(new IdempotencyKeyReusedException(), "IDEMPOTENCY_KEY_REUSED", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("RESERVATION_IN_PROGRESS는 409 CONFLICT를 노출한다")
    void reservationInProgress_exposesCodeAndHttpStatus() {
        assertErrorCode(new ReservationInProgressException(), "RESERVATION_IN_PROGRESS", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("선점 에러 코드는 SCREAMING_SNAKE_CASE이다")
    void reservationErrorCode_isScreamingSnakeCase() {
        BusinessException exception = new InvalidReservationQuantityException();

        assertThat(exception.getCode()).matches(SCREAMING_SNAKE_CASE);
    }

    private void assertErrorCode(BusinessException exception, String expectedCode, HttpStatus expectedHttpStatus) {
        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
        assertThat(exception.getHttpStatus()).isEqualTo(expectedHttpStatus);
    }
}
