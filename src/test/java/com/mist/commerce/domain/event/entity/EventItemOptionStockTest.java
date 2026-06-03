package com.mist.commerce.domain.event.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
