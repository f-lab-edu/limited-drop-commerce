package com.mist.commerce.domain.event.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_item_option_stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventItemOptionStock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "product_option_value_id", nullable = false)
    private Long productOptionValueId;

    @Column(name = "stock_quantity")
    private int stockQuantity;

    private EventItemOptionStock(Long productOptionValueId, int stockQuantity) {
        validate(stockQuantity);
        this.productOptionValueId = productOptionValueId;
        this.stockQuantity = stockQuantity;
    }

    public static EventItemOptionStock create(Long productOptionValueId, int stockQuantity) {
        return new EventItemOptionStock(productOptionValueId, stockQuantity);
    }

    private static void validate(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }
}
