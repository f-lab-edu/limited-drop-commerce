package com.mist.commerce.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.reservation.application.listener.ReservationCreatedAfterCommitEvent;
import com.mist.commerce.domain.reservation.application.service.ReservationOptionStockService;
import com.mist.commerce.domain.reservation.application.service.ReservationService;
import com.mist.commerce.domain.reservation.application.support.ReservationCreator;
import com.mist.commerce.domain.reservation.application.support.ReservationValidator;
import com.mist.commerce.domain.reservation.application.support.ReserveContextLoader;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ReservationService는 예약 흐름을 조립하는 오케스트레이터다.
 * 컨텍스트 로딩·검증·재고 선점·주문 생성은 각 협력자로 위임되므로,
 * 이 테스트는 위임 순서와 예외 전파, 커밋 후 이벤트 발행만 검증한다.
 * 개별 규칙(검증/재고/컨텍스트 로딩) 검증은 각 협력자 단위 테스트가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 20L;
    private static final Long EVENT_ITEM_ID = 30L;
    private static final Long OPTION_STOCK_ID = 40L;
    private static final Long PRODUCT_OPTION_GROUP_ID = 50L;
    private static final Long PRODUCT_OPTION_VALUE_ID = 60L;
    private static final String IDEMPOTENCY_KEY = "reservation-idem-key-001";

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ReserveContextLoader contextLoader;

    @Mock
    private ReservationValidator validator;

    @Mock
    private ReservationCreator reservationCreator;

    @Mock
    private ReservationOptionStockService reservationOptionStockService;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                eventPublisher,
                contextLoader,
                validator,
                reservationCreator,
                reservationOptionStockService);
    }

    @Test
    @DisplayName("예약 성공: 로딩→검증→재고 선점→생성→이벤트 발행 순으로 위임하고 생성 결과를 반환한다")
    void reserve_success_delegatesInOrderAndReturnsCreatedResult() {
        ReserveCommand command = command(2);
        EventItemOptionStock optionStock = optionStock();
        ReserveContext context = ReserveContext.builder()
                .eventItemOptionStock(optionStock)
                .build();
        ReserveResult result = new ReserveResult(
                1000L,
                LocalDateTime.parse("2026-06-17T12:30:00"),
                "PENDING_PAYMENT");

        when(contextLoader.load(command)).thenReturn(context);
        when(reservationCreator.create(command, context)).thenReturn(result);

        ReserveResult actual = reservationService.reserve(command);

        assertThat(actual).isSameAs(result);

        InOrder ordered = inOrder(
                contextLoader,
                validator,
                reservationOptionStockService,
                reservationCreator,
                eventPublisher);
        ordered.verify(contextLoader).load(command);
        ordered.verify(validator).validate(command, context);
        ordered.verify(reservationOptionStockService).reserve(optionStock, command);
        ordered.verify(reservationCreator).create(command, context);

        ArgumentCaptor<ReservationCreatedAfterCommitEvent> eventCaptor =
                ArgumentCaptor.forClass(ReservationCreatedAfterCommitEvent.class);
        ordered.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(eventCaptor.getValue().result()).isSameAs(result);
    }

    @Test
    @DisplayName("컨텍스트 로딩 실패 시 이후 단계를 진행하지 않고 예외를 전파한다")
    void reserve_whenContextLoadFails_propagatesAndSkipsEverything() {
        ReserveCommand command = command(2);
        when(contextLoader.load(command)).thenThrow(new IllegalStateException("event not found"));

        assertThatThrownBy(() -> reservationService.reserve(command))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(validator, reservationOptionStockService, reservationCreator);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("검증 실패 시 재고 선점·주문 생성·이벤트 발행을 하지 않고 예외를 전파한다")
    void reserve_whenValidationFails_propagatesAndSkipsSubsequentSteps() {
        ReserveCommand command = command(2);
        ReserveContext context = ReserveContext.builder()
                .eventItemOptionStock(optionStock())
                .build();
        when(contextLoader.load(command)).thenReturn(context);
        doThrow(new IllegalStateException("invalid")).when(validator).validate(command, context);

        assertThatThrownBy(() -> reservationService.reserve(command))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(reservationOptionStockService, reservationCreator);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("재고 선점 실패 시 주문 생성·이벤트 발행을 하지 않고 예외를 전파한다")
    void reserve_whenStockReservationFails_propagatesAndSkipsCreation() {
        ReserveCommand command = command(2);
        EventItemOptionStock optionStock = optionStock();
        ReserveContext context = ReserveContext.builder()
                .eventItemOptionStock(optionStock)
                .build();
        when(contextLoader.load(command)).thenReturn(context);
        doThrow(new IllegalStateException("sold out"))
                .when(reservationOptionStockService).reserve(optionStock, command);

        assertThatThrownBy(() -> reservationService.reserve(command))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(reservationCreator);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private ReserveCommand command(int quantity) {
        return new ReserveCommand(USER_ID, EVENT_ID, EVENT_ITEM_ID, OPTION_STOCK_ID, quantity, IDEMPOTENCY_KEY);
    }

    private EventItemOptionStock optionStock() {
        EventItemOptionStock optionStock = EventItemOptionStock.create(
                PRODUCT_OPTION_GROUP_ID,
                PRODUCT_OPTION_VALUE_ID,
                10);
        ReflectionTestUtils.setField(optionStock, "id", OPTION_STOCK_ID);
        return optionStock;
    }
}
