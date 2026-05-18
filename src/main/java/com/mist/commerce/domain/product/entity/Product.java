package com.mist.commerce.domain.product.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
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
        if (brandId == null) {
            throw new IllegalArgumentException("brandId must not be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("price must be greater than or equal to 0");
        }
        if (status == null || !status.isCreatable()) {
            throw new IllegalArgumentException("status must be DRAFT or ACTIVE");
        }
    }
}
