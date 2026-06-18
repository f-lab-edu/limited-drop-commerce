package com.mist.commerce.domain.event.entity;

import com.mist.commerce.domain.event.exception.InsufficientStockException;
import com.mist.commerce.domain.event.exception.StockExhaustedException;
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

    @Column(name = "product_option_group_id", nullable = false)
    private Long productOptionGroupId;

    @Column(name = "product_option_value_id", nullable = false)
    private Long productOptionValueId;

    @Column(name = "stock_quantity")
    private int stockQuantity;

    @Column(name = "reserved_quantity")
    private int reservedQuantity;

    private EventItemOptionStock(Long productOptionGroupId, Long productOptionValueId, int stockQuantity) {
        validate(stockQuantity);
        this.productOptionGroupId = productOptionGroupId;
        this.productOptionValueId = productOptionValueId;
        this.stockQuantity = stockQuantity;
        this.reservedQuantity = 0;
    }

    public static EventItemOptionStock create(Long productOptionGroupId, Long productOptionValueId, int stockQuantity) {
        return new EventItemOptionStock(productOptionGroupId, productOptionValueId, stockQuantity);
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        int availableStock = stockQuantity - reservedQuantity;
        if (availableStock == 0 && quantity > 0) {
            throw new StockExhaustedException();
        }
        if (availableStock < quantity) {
            throw new InsufficientStockException();
        }

        reservedQuantity += quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (quantity > reservedQuantity) {
            throw new IllegalArgumentException("Release quantity cannot exceed reserved quantity");
        }

        reservedQuantity -= quantity;
    }

    public void confirm(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (quantity > reservedQuantity) {
            throw new IllegalArgumentException("Confirm quantity cannot exceed reserved quantity");
        }

        stockQuantity -= quantity;
        reservedQuantity -= quantity;
    }

    private static void validate(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
    }
}
