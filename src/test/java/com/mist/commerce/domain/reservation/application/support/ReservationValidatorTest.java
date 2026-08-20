package com.mist.commerce.domain.reservation.application.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.entity.EventStatus;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationValidatorTest {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 20L;
    private static final Long EVENT_ITEM_ID = 30L;
    private static final Long OPTION_STOCK_ID = 40L;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReservationValidator validator;

    @Test
    @DisplayName("정상: 수량·이벤트 상태·중복 예약을 모두 통과하면 예외가 없다")
    void validate_whenAllRulesPass_doesNotThrow() {
        ReserveContext context = context(openEvent(item(3, 10)));
        when(orderRepository.existsByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, OrderStatus.PENDING_PAYMENT))
                .thenReturn(false);

        assertThatCode(() -> validator.validate(command(2), context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("수량이 0이면 INVALID_RESERVATION_QUANTITY를 던진다")
    void validate_whenQuantityIsZero_throwsInvalidReservationQuantity() {
        ReserveContext context = context(openEvent(item(3, 10)));

        assertBusinessException("INVALID_RESERVATION_QUANTITY", () -> validator.validate(command(0), context));
    }

    @Test
    @DisplayName("수량이 음수이면 INVALID_RESERVATION_QUANTITY를 던진다")
    void validate_whenQuantityIsNegative_throwsInvalidReservationQuantity() {
        ReserveContext context = context(openEvent(item(3, 10)));

        assertBusinessException("INVALID_RESERVATION_QUANTITY", () -> validator.validate(command(-1), context));
    }

    @Test
    @DisplayName("구매 제한을 초과하면 PURCHASE_LIMIT_EXCEEDED를 던진다")
    void validate_whenQuantityExceedsPurchaseLimit_throwsPurchaseLimitExceeded() {
        ReserveContext context = context(openEvent(item(1, 10)));

        assertBusinessException("PURCHASE_LIMIT_EXCEEDED", () -> validator.validate(command(2), context));
    }

    @Test
    @DisplayName("READY 이벤트면 DROP_EVENT_NOT_OPEN을 던진다")
    void validate_whenEventIsReady_throwsDropEventNotOpen() {
        ReserveContext context = context(event(EventStatus.READY, item(3, 10)));

        assertBusinessException("DROP_EVENT_NOT_OPEN", () -> validator.validate(command(2), context));
    }

    @Test
    @DisplayName("CLOSED 이벤트면 DROP_EVENT_CLOSED를 던진다")
    void validate_whenEventIsClosed_throwsDropEventClosed() {
        ReserveContext context = context(event(EventStatus.CLOSED, item(3, 10)));

        assertBusinessException("DROP_EVENT_CLOSED", () -> validator.validate(command(2), context));
    }

    @Test
    @DisplayName("같은 사용자와 이벤트에 결제 대기 주문이 있으면 ACTIVE_RESERVATION_ALREADY_EXISTS를 던진다")
    void validate_whenActiveReservationExists_throwsActiveReservationAlreadyExists() {
        ReserveContext context = context(openEvent(item(3, 10)));
        when(orderRepository.existsByUserIdAndEventIdAndStatus(USER_ID, EVENT_ID, OrderStatus.PENDING_PAYMENT))
                .thenReturn(true);

        assertBusinessException("ACTIVE_RESERVATION_ALREADY_EXISTS", () -> validator.validate(command(2), context));
    }

    private void assertBusinessException(String expectedCode, ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private ReserveContext context(Event event) {
        return ReserveContext.builder()
                .event(event)
                .eventItem(event.getItems().getFirst())
                .eventItemOptionStock(event.getItems().getFirst().getOptionStocks().getFirst())
                .build();
    }

    private ReserveCommand command(int quantity) {
        return new ReserveCommand(USER_ID, EVENT_ID, EVENT_ITEM_ID, OPTION_STOCK_ID, quantity);
    }

    private Event openEvent(EventItem eventItem) {
        return event(EventStatus.OPEN, eventItem);
    }

    private Event event(EventStatus status, EventItem eventItem) {
        Event event = Event.create(
                1L,
                "한정 스니커즈 드롭",
                Instant.parse("2026-06-17T00:00:00Z"),
                Instant.parse("2026-06-17T06:00:00Z"),
                List.of(eventItem));
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        ReflectionTestUtils.setField(event, "status", status);
        return event;
    }

    private EventItem item(int maxPurchasePerCustomer, int quantity) {
        EventItem eventItem = EventItem.create(
                100L,
                new BigDecimal("150000"),
                quantity,
                maxPurchasePerCustomer,
                List.of(optionStock(quantity)));
        ReflectionTestUtils.setField(eventItem, "id", EVENT_ITEM_ID);
        return eventItem;
    }

    private EventItemOptionStock optionStock(int stockQuantity) {
        EventItemOptionStock optionStock = EventItemOptionStock.create(50L, 60L, stockQuantity);
        ReflectionTestUtils.setField(optionStock, "id", OPTION_STOCK_ID);
        return optionStock;
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
