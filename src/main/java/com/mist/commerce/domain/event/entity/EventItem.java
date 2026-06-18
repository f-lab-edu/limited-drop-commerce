package com.mist.commerce.domain.event.entity;

import com.mist.commerce.domain.reservation.exception.InvalidReservationQuantityException;
import com.mist.commerce.domain.reservation.exception.PurchaseLimitExceededException;
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

    @Column(name = "max_purchase_per_customer")
    private int maxPurchasePerCustomer;

    @Column(name = "reserved_quantity")
    private int reservedQuantity;

    @Column(name = "sold_quantity")
    private int soldQuantity;

    @OneToMany(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "event_item_id")
    private List<EventItemOptionStock> optionStocks;

    private EventItem(
            Long productId,
            BigDecimal price,
            int quantity,
            int maxPurchasePerCustomer,
            List<EventItemOptionStock> optionStocks
    ) {
        validate(price, quantity, maxPurchasePerCustomer);
        this.productId = productId;
        this.price = price;
        this.quantity = quantity;
        this.maxPurchasePerCustomer = maxPurchasePerCustomer;
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
        return create(productId, price, quantity, Integer.MAX_VALUE, optionStocks);
    }

    public static EventItem create(
            Long productId,
            BigDecimal price,
            int quantity,
            int maxPurchasePerCustomer,
            List<EventItemOptionStock> optionStocks
    ) {
        return new EventItem(productId, price, quantity, maxPurchasePerCustomer, optionStocks);
    }

    public void verifyPurchasableQuantity(int requestedQuantity, int alreadyPurchasedQuantity) {
        if (requestedQuantity < 1) {
            throw new InvalidReservationQuantityException();
        }
        if (alreadyPurchasedQuantity + requestedQuantity > maxPurchasePerCustomer) {
            throw new PurchaseLimitExceededException();
        }
    }

    private static void validate(BigDecimal price, int quantity, int maxPurchasePerCustomer) {
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        if (maxPurchasePerCustomer < 1) {
            throw new IllegalArgumentException("Max purchase per customer must be positive");
        }
    }
}
