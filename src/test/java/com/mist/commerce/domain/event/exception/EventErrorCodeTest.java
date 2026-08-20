package com.mist.commerce.domain.event.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.global.exception.BusinessException;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class EventErrorCodeTest {

    private static final Pattern SCREAMING_SNAKE_CASE = Pattern.compile("^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*$");

    @Test
    @DisplayName("DROP_EVENT_NOT_FOUND는 404 NOT_FOUND를 노출한다")
    void dropEventNotFound_exposesCodeAndHttpStatus() {
        assertErrorCode(new EventNotFoundException(), "DROP_EVENT_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DROP_EVENT_NOT_OPEN은 400 BAD_REQUEST를 노출한다")
    void dropEventNotOpen_exposesCodeAndHttpStatus() {
        assertErrorCode(new DropEventNotOpenException(), "DROP_EVENT_NOT_OPEN", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("DROP_EVENT_CLOSED는 400 BAD_REQUEST를 노출한다")
    void dropEventClosed_exposesCodeAndHttpStatus() {
        assertErrorCode(new DropEventClosedException(), "DROP_EVENT_CLOSED", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("EVENT_ITEM_OPTION_NOT_FOUND는 404 NOT_FOUND를 노출한다")
    void eventItemOptionNotFound_exposesCodeAndHttpStatus() {
        assertErrorCode(new EventItemOptionNotFoundException(), "EVENT_ITEM_OPTION_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("INSUFFICIENT_STOCK은 400 BAD_REQUEST를 노출한다")
    void insufficientStock_exposesCodeAndHttpStatus() {
        assertErrorCode(new InsufficientStockException(), "INSUFFICIENT_STOCK", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("STOCK_EXHAUSTED는 400 BAD_REQUEST를 노출한다")
    void stockExhausted_exposesCodeAndHttpStatus() {
        assertErrorCode(new StockExhaustedException(), "STOCK_EXHAUSTED", HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("이벤트 에러 코드는 SCREAMING_SNAKE_CASE이다")
    void eventErrorCode_isScreamingSnakeCase() {
        BusinessException exception = new InsufficientStockException();

        assertThat(exception.getCode()).matches(SCREAMING_SNAKE_CASE);
    }

    private void assertErrorCode(BusinessException exception, String expectedCode, HttpStatus expectedHttpStatus) {
        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getCode()).isEqualTo(expectedCode);
        assertThat(exception.getHttpStatus()).isEqualTo(expectedHttpStatus);
    }
}
