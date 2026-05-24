package com.mist.commerce.domain.product.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import io.jsonwebtoken.lang.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(toBuilder = true)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    public static Product create(
            Long brandId,
            Long userId,
            String name,
            String description,
            Long price,
            ProductStatus status
    ) {
        validate(brandId, userId, name, price, status);

        return Product.builder()
                .brandId(brandId)
                .userId(userId)
                .name(name)
                .description(description)
                .price(price)
                .status(status)
                .build();
    }

    private static void validate(Long brandId, Long userId, String name, Long price, ProductStatus status) {
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(price >= 0, "price must be greater than or equal to 0");
        Assert.isTrue(status.isCreatable(), "status must be DRAFT or ACTIVE");
    }
}
