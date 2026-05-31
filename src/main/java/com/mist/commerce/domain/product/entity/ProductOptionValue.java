package com.mist.commerce.domain.product.entity;

import com.mist.commerce.global.entity.BaseTimeEntity;
import io.jsonwebtoken.lang.Assert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "product_option_value",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pov_group_id_value",
                columnNames = {"option_group_id", "value"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionValue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private ProductOptionGroup optionGroup;

    @Column(nullable = false, length = 20)
    private String value;

    private ProductOptionValue(String value) {
        Assert.hasText(value, "value must not be empty");
        this.value = value;
    }

    static ProductOptionValue create(String value) {
        return new ProductOptionValue(value);
    }

    void setOptionGroup(ProductOptionGroup optionGroup) {
        this.optionGroup = optionGroup;
    }
}
