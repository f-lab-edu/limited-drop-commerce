package com.mist.commerce.domain.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.reservation.application.listener.OptionStockReservedEvent;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.repository.OptionStockStore;
import com.mist.commerce.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationOptionStockServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long EVENT_ID = 20L;
    private static final Long EVENT_ITEM_ID = 30L;
    private static final Long OPTION_STOCK_ID = 40L;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OptionStockStore optionStockStore;

    @InjectMocks
    private ReservationOptionStockService reservationOptionStockService;

    @Test
    @DisplayName("정상: 재고를 차감하고 선점 수량을 반영한 뒤 재고 선점 이벤트를 발행한다")
    void reserve_whenStockAvailable_reservesAndPublishesEvent() {
        EventItemOptionStock optionStock = optionStock(10, 0);
        when(optionStockStore.tryDecrease(OPTION_STOCK_ID, 2, 10)).thenReturn(8L);

        reservationOptionStockService.reserve(optionStock, command(2));

        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);

        ArgumentCaptor<OptionStockReservedEvent> captor = ArgumentCaptor.forClass(OptionStockReservedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().optionStockId()).isEqualTo(OPTION_STOCK_ID);
        assertThat(captor.getValue().quantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("가용 재고가 부족하면 INSUFFICIENT_STOCK을 던지고 선점·이벤트 발행을 하지 않는다")
    void reserve_whenAvailableStockIsLowerThanQuantity_throwsInsufficientStock() {
        EventItemOptionStock optionStock = optionStock(10, 9);
        when(optionStockStore.tryDecrease(OPTION_STOCK_ID, 2, 1)).thenReturn(-1L);

        assertBusinessException("INSUFFICIENT_STOCK",
                () -> reservationOptionStockService.reserve(optionStock, command(2)));

        assertThat(optionStock.getReservedQuantity()).isEqualTo(9);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("가용 재고가 0이면 STOCK_EXHAUSTED를 던지고 선점·이벤트 발행을 하지 않는다")
    void reserve_whenAvailableStockIsZero_throwsStockExhausted() {
        EventItemOptionStock optionStock = optionStock(0, 0);
        when(optionStockStore.tryDecrease(OPTION_STOCK_ID, 1, 0)).thenReturn(-1L);

        assertBusinessException("STOCK_EXHAUSTED",
                () -> reservationOptionStockService.reserve(optionStock, command(1)));

        assertThat(optionStock.getReservedQuantity()).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    private void assertBusinessException(String expectedCode, ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    private ReserveCommand command(int quantity) {
        return new ReserveCommand(USER_ID, EVENT_ID, EVENT_ITEM_ID, OPTION_STOCK_ID, quantity);
    }

    private EventItemOptionStock optionStock(int stockQuantity, int reservedQuantity) {
        EventItemOptionStock optionStock = EventItemOptionStock.create(50L, 60L, stockQuantity);
        ReflectionTestUtils.setField(optionStock, "id", OPTION_STOCK_ID);
        ReflectionTestUtils.setField(optionStock, "reservedQuantity", reservedQuantity);
        return optionStock;
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
