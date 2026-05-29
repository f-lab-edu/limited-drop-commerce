package com.mist.commerce.domain.product.entity;

import com.mist.commerce.domain.product.exception.ProductOptionGroupNameDuplicatedException;
import com.mist.commerce.global.entity.BaseTimeEntity;
import io.jsonwebtoken.lang.Assert;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity(name = "product")
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder(access = AccessLevel.PRIVATE, toBuilder = true)
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

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductOptionGroup> optionGroups = new ArrayList<>();

    @Builder
    public record OptionGroupSpec(String name, int displayOrder, boolean required, List<String> values) {
    }

    public static Product create(
            Long brandId,
            Long userId,
            String name,
            String description,
            Long price,
            ProductStatus status
    ) {
        return create(brandId, userId, name, description, price, status, List.of());
    }

    public static Product create(
            Long brandId,
            Long userId,
            String name,
            String description,
            Long price,
            ProductStatus status,
            List<OptionGroupSpec> optionGroupSpecs
    ) {
        validate(name, price, status);

        Product product = Product.builder()
                .brandId(brandId)
                .userId(userId)
                .name(name)
                .description(description)
                .price(price)
                .status(status)
                .build();

        Set<String> groupNames = new HashSet<>();
        for (OptionGroupSpec optionGroupSpec : optionGroupSpecs) {
            ProductOptionGroup optionGroup = ProductOptionGroup.create(
                    optionGroupSpec.name(),
                    optionGroupSpec.displayOrder(),
                    optionGroupSpec.required(),
                    optionGroupSpec.values()
            );
            if (!groupNames.add(optionGroup.getName())) {
                throw new ProductOptionGroupNameDuplicatedException(optionGroup.getName());
            }
            product.addOptionGroup(optionGroup);
        }

        return product;
    }

    private static void validate(String name, Long price, ProductStatus status) {
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(price >= 0, "price must be greater than or equal to 0");
        Assert.isTrue(status.isCreatable(), "status must be READY");
    }

    private void addOptionGroup(ProductOptionGroup optionGroup) {
        this.optionGroups.add(optionGroup);
        optionGroup.setProduct(this);
    }

    public List<ProductOptionGroup> getOptionGroups() {
        return Collections.unmodifiableList(optionGroups);
    }
}
