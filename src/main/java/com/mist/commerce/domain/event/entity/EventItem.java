package com.mist.commerce.domain.event.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "price", precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "reserved_quantity")
    private int reservedQuantity;

    @Column(name = "sold_quantity")
    private int soldQuantity;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "event_item_id")
    private List<EventItemOptionStock> optionStocks;

    private EventItem(Long productId, BigDecimal price, int quantity, List<EventItemOptionStock> optionStocks) {
        validate(price, quantity);
        this.productId = productId;
        this.price = price;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.soldQuantity = 0;
        this.optionStocks = optionStocks;
    }

    public static EventItem create(
            Long productId,
            BigDecimal price,
            int quantity,
            List<EventItemOptionStock> optionStocks
    ) {
        return new EventItem(productId, price, quantity, optionStocks);
    }

    private static void validate(BigDecimal price, int quantity) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
    }
}
