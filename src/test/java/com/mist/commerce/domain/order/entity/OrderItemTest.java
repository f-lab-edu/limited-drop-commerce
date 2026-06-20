package com.mist.commerce.domain.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    @Test
    @DisplayName("TC-ORDER-005: OrderItem 생성 시 주문 시점 스냅샷 필드가 보존된다")
    void create_preservesOrderItemSnapshotFields() {
        OrderItem item = OrderItem.create(
                10L,
                20L,
                "색상",
                "Black",
                new BigDecimal("150000"),
                2);

        assertThat(item.getEventItemId()).isEqualTo(10L);
        assertThat(item.getEventItemOptionId()).isEqualTo(20L);
        assertThat(item.getProductOptionGroupName()).isEqualTo("색상");
        assertThat(item.getProductOptionValueName()).isEqualTo("Black");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("150000");
        assertThat(item.getQuantity()).isEqualTo(2);
    }
}
