package com.mist.commerce.domain.product.entity;

import com.mist.commerce.domain.product.exception.ProductOptionValueDuplicatedException;
import com.mist.commerce.domain.product.exception.ProductOptionValueRequiredException;
import com.mist.commerce.global.entity.BaseTimeEntity;
import io.jsonwebtoken.lang.Assert;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(
        name = "product_option_group",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_pog_product_id_name",
                columnNames = {"product_id", "name"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOptionGroup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "display_order")
    private int displayOrder;

    private boolean required;

    @OneToMany(mappedBy = "optionGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOptionValue> optionValues = new ArrayList<>();

    private ProductOptionGroup(String name, int displayOrder, boolean required) {
        this.name = name;
        this.displayOrder = displayOrder;
        this.required = required;
    }

    static ProductOptionGroup create(String name, int displayOrder, boolean required, List<String> values) {
        Assert.hasText(name, "name must not be empty");
        Assert.isTrue(displayOrder >= 0, "displayOrder must be greater than or equal to 0");
        if (values.isEmpty()) {
            throw new ProductOptionValueRequiredException(name);
        }

        ProductOptionGroup optionGroup = new ProductOptionGroup(name, displayOrder, required);
        Set<String> optionValues = new HashSet<>();
        for (String value : values) {
            ProductOptionValue optionValue = ProductOptionValue.create(value);
            if (!optionValues.add(optionValue.getValue())) {
                throw new ProductOptionValueDuplicatedException(optionValue.getValue());
            }
            optionGroup.addOptionValue(optionValue);
        }

        return optionGroup;
    }

    void setProduct(Product product) {
        this.product = product;
    }

    private void addOptionValue(ProductOptionValue optionValue) {
        this.optionValues.add(optionValue);
        optionValue.setOptionGroup(this);
    }

    public List<ProductOptionValue> getOptionValues() {
        return Collections.unmodifiableList(optionValues);
    }
}
