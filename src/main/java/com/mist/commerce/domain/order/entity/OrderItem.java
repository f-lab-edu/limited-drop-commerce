package com.mist.commerce.domain.order.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "event_item_id")
    private Long eventItemId;

    @Column(name = "event_item_option_id")
    private Long eventItemOptionId;

    @Column(name = "product_option_group_name")
    private String productOptionGroupName;

    @Column(name = "product_option_value_name")
    private String productOptionValueName;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity")
    private int quantity;

    private OrderItem(
            Long eventItemId,
            Long eventItemOptionId,
            String productOptionGroupName,
            String productOptionValueName,
            BigDecimal unitPrice,
            int quantity
    ) {
        this.eventItemId = eventItemId;
        this.eventItemOptionId = eventItemOptionId;
        this.productOptionGroupName = productOptionGroupName;
        this.productOptionValueName = productOptionValueName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem create(
            Long eventItemId,
            Long eventItemOptionId,
            String productOptionGroupName,
            String productOptionValueName,
            BigDecimal unitPrice,
            int quantity
    ) {
        return new OrderItem(
                eventItemId,
                eventItemOptionId,
                productOptionGroupName,
                productOptionValueName,
                unitPrice,
                quantity);
    }
}
