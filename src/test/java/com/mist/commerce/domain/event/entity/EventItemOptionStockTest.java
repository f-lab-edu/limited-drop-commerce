package com.mist.commerce.domain.event.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EventItemOptionStockTest {

    @Test
    @DisplayName("create가 옵션 그룹 ID, 옵션 값 ID, 재고 수량을 모두 보존한다")
    void create_withGroupIdValueIdAndStockQuantity_preservesAllValues() {
        EventItemOptionStock optionStock = EventItemOptionStock.create(3L, 5L, 40);

        assertThat(optionStock.getProductOptionGroupId()).isEqualTo(3L);
        assertThat(optionStock.getProductOptionValueId()).isEqualTo(5L);
        assertThat(optionStock.getStockQuantity()).isEqualTo(40);
    }

    @Test
    @DisplayName("stockQuantity가 음수이면 기존 IllegalArgumentException 동작을 유지한다")
    void create_withNegativeStockQuantity_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> EventItemOptionStock.create(3L, 5L, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TC-EIOS-001: 판매 가능 재고가 요청 수량보다 크면 reservedQuantity가 증가한다")
    void reserve_whenAvailableStockIsGreaterThanQuantity_increasesReservedQuantity() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        optionStock.reserve(3);

        assertThat(optionStock.getReservedQuantity()).isEqualTo(5);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(availableStock(optionStock)).isEqualTo(5);
    }

    @Test
    @DisplayName("TC-EIOS-002: 판매 가능 재고와 정확히 같은 수량을 선점할 수 있다")
    void reserve_whenQuantityEqualsAvailableStock_increasesReservedQuantityToStockQuantity() {
        EventItemOptionStock optionStock = optionStock(10, 4);

        optionStock.reserve(6);

        assertThat(optionStock.getReservedQuantity()).isEqualTo(10);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(availableStock(optionStock)).isZero();
    }

    @Test
    @DisplayName("TC-EIOS-003: 판매 가능 재고보다 1개 많은 수량을 요청하면 INSUFFICIENT_STOCK 예외가 발생한다")
    void reserve_whenQuantityExceedsAvailableStockByOne_throwsInsufficientStock() {
        EventItemOptionStock optionStock = optionStock(10, 4);

        assertThatThrownBy(() -> optionStock.reserve(7))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_STOCK");
        assertThat(optionStock.getReservedQuantity()).isEqualTo(4);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC-EIOS-004: 판매 가능 재고가 0이면 STOCK_EXHAUSTED 예외가 발생한다")
    void reserve_whenAvailableStockIsZero_throwsStockExhausted() {
        EventItemOptionStock optionStock = optionStock(10, 10);

        assertThatThrownBy(() -> optionStock.reserve(1))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("STOCK_EXHAUSTED");
        assertThat(optionStock.getReservedQuantity()).isEqualTo(10);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC-EIOS-005: 만료 또는 취소 복구 시 reservedQuantity가 감소한다")
    void release_whenQuantityIsWithinReservedQuantity_decreasesReservedQuantity() {
        EventItemOptionStock optionStock = optionStock(10, 6);

        optionStock.release(4);

        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(availableStock(optionStock)).isEqualTo(8);
    }

    @Test
    @DisplayName("TC-EIOS-006: 복구 후 같은 수량을 다시 선점할 수 있다")
    void reserve_afterRelease_canReserveReleasedQuantityAgain() {
        EventItemOptionStock optionStock = optionStock(10, 10);

        optionStock.release(3);

        assertThat(optionStock.getReservedQuantity()).isEqualTo(7);
        assertThat(availableStock(optionStock)).isEqualTo(3);

        optionStock.reserve(3);

        assertThat(optionStock.getReservedQuantity()).isEqualTo(10);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(availableStock(optionStock)).isZero();
    }

    @Test
    @DisplayName("TC-EIOS-007: 복구 수량이 현재 reservedQuantity보다 크면 예외가 발생하고 상태가 유지된다")
    void release_whenQuantityExceedsReservedQuantity_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.release(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC-EIOS-008: 결제 확정 시 stockQuantity와 reservedQuantity가 동시에 감소한다")
    void confirm_whenQuantityIsWithinReservedQuantity_decreasesStockAndReservedQuantity() {
        EventItemOptionStock optionStock = optionStock(10, 4);

        optionStock.confirm(3);

        assertThat(optionStock.getStockQuantity()).isEqualTo(7);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(1);
        assertThat(availableStock(optionStock)).isEqualTo(6);
    }

    @Test
    @DisplayName("TC-EIOS-009: 확정 수량이 현재 reservedQuantity보다 크면 예외가 발생하고 상태가 유지된다")
    void confirm_whenQuantityExceedsReservedQuantity_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.confirm(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-EIOS-011: 선점 수량이 0이면 예외가 발생하고 상태가 유지된다")
    void reserve_whenQuantityIsZero_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.reserve(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-EIOS-012: 선점 수량이 음수이면 예외가 발생하고 상태가 유지된다")
    void reserve_whenQuantityIsNegative_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.reserve(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-EIOS-013: 품절 상태라도 음수 선점은 STOCK_EXHAUSTED보다 IllegalArgumentException이 우선한다")
    void reserve_whenQuantityIsNegativeAndStockExhausted_throwsIllegalArgumentExceptionFirst() {
        EventItemOptionStock optionStock = optionStock(10, 10);

        assertThatThrownBy(() -> optionStock.reserve(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("TC-EIOS-014: 복구 수량이 음수이면 예외가 발생하고 상태가 유지된다")
    void release_whenQuantityIsNegative_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.release(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-EIOS-015: 확정 수량이 음수이면 예외가 발생하고 상태가 유지된다")
    void confirm_whenQuantityIsNegative_throwsIllegalArgumentExceptionAndKeepsState() {
        EventItemOptionStock optionStock = optionStock(10, 2);

        assertThatThrownBy(() -> optionStock.confirm(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(optionStock.getStockQuantity()).isEqualTo(10);
        assertThat(optionStock.getReservedQuantity()).isEqualTo(2);
    }

    private EventItemOptionStock optionStock(int stockQuantity, int reservedQuantity) {
        EventItemOptionStock optionStock = EventItemOptionStock.create(3L, 5L, stockQuantity);
        ReflectionTestUtils.setField(optionStock, "reservedQuantity", reservedQuantity);
        return optionStock;
    }

    private int availableStock(EventItemOptionStock optionStock) {
        return optionStock.getStockQuantity() - optionStock.getReservedQuantity();
    }
}
