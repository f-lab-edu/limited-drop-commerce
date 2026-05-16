package com.mist.commerce.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "Limited Sneakers";
    private static final String DESCRIPTION = "2026 한정판";
    private static final Long PRICE = 150_000L;

    @Test
    @DisplayName("유효한 입력으로 상품을 생성하면 입력 필드가 그대로 채워진다")
    void create_withValidInput_returnsProductWithFields() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, ProductStatus.DRAFT);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(NAME);
        assertThat(product.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(product.getPrice()).isEqualTo(PRICE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.getId()).isNull();
    }

    @Test
    @DisplayName("상품명이 null이면 상품 생성에 실패한다")
    void create_whenNameIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, null, DESCRIPTION, PRICE, ProductStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("상품명이 blank이면 상품 생성에 실패한다")
    void create_whenNameIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, "   ", DESCRIPTION, PRICE, ProductStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("가격이 음수이면 상품 생성에 실패한다")
    void create_whenPriceIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, NAME, DESCRIPTION, -1L, ProductStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    @DisplayName("등록 불가능 상태인 INACTIVE로 상품을 생성하면 실패한다")
    void create_whenStatusIsInactive_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, ProductStatus.INACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("브랜드 ID가 null이면 상품 생성에 실패한다")
    void create_whenBrandIdIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(null, NAME, DESCRIPTION, PRICE, ProductStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brandId");
    }

    @Test
    @DisplayName("상품 설명이 null이어도 상품을 생성할 수 있다")
    void create_whenDescriptionIsNull_returnsProductWithNullDescription() {
        Product product = Product.create(BRAND_ID, NAME, null, PRICE, ProductStatus.DRAFT);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(NAME);
        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isEqualTo(PRICE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    @DisplayName("등록 가능 상태인 ACTIVE로 상품을 생성할 수 있다")
    void create_whenStatusIsActive_returnsProductWithActiveStatus() {
        Product product = Product.create(BRAND_ID, NAME, DESCRIPTION, PRICE, ProductStatus.ACTIVE);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
    }
}
