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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

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
            String name,
            String description,
            Long price,
            ProductStatus status
    ) {
        validate(brandId, name, price, status);

        Product product = new Product();
        product.brandId = brandId;
        product.name = name;
        product.description = description;
        product.price = price;
        product.status = status;
        return product;
    }

    private static void validate(Long brandId, String name, Long price, ProductStatus status) {
        if (brandId == null) {
            throw new IllegalArgumentException("brandId must not be null");
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
