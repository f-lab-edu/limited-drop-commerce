package com.mist.commerce.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.product.exception.ProductOptionGroupNameDuplicatedException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductTest {

    private static final Long BRAND_ID = 1L;
    private static final String NAME = "Limited Sneakers";
    private static final String DESCRIPTION = "2026 한정판";
    private static final Long PRICE = 150_000L;
    private static final Long USER_ID = 10L;

    @Test
    @DisplayName("유효한 입력으로 상품을 생성하면 입력 필드가 그대로 채워진다")
    void create_withValidInput_returnsProductWithFields() {
        Product product = Product.create(
                BRAND_ID,
                USER_ID,
                NAME,
                DESCRIPTION,
                PRICE,
                ProductStatus.READY);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(NAME);
        assertThat(product.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(product.getPrice()).isEqualTo(PRICE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.READY);
        assertThat(product.getId()).isNull();
    }

    @Test
    @DisplayName("상품명이 null이면 상품 생성에 실패한다")
    void create_whenNameIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, USER_ID, null, DESCRIPTION, PRICE, ProductStatus.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("상품명이 blank이면 상품 생성에 실패한다")
    void create_whenNameIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, USER_ID, "   ", DESCRIPTION, PRICE, ProductStatus.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("가격이 음수이면 상품 생성에 실패한다")
    void create_whenPriceIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, USER_ID, NAME, DESCRIPTION, -1L, ProductStatus.READY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    @DisplayName("등록 불가능 상태인 ON_SALE로 상품을 생성하면 실패한다")
    void create_whenStatusIsInactive_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Product.create(BRAND_ID, USER_ID, NAME, DESCRIPTION, PRICE, ProductStatus.ON_SALE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("브랜드 ID가 null이어도 상품을 생성할 수 있다")
    void create_whenBrandIdIsNull_returnsProductWithNullBrandId() {
        assertThatCode(() -> {
            Product product = Product.create(null, USER_ID, NAME, DESCRIPTION, PRICE, ProductStatus.READY);

            assertThat(product.getBrandId()).isNull();
            assertThat(product.getName()).isEqualTo(NAME);
            assertThat(product.getPrice()).isEqualTo(PRICE);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.READY);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("상품 설명이 null이어도 상품을 생성할 수 있다")
    void create_whenDescriptionIsNull_returnsProductWithNullDescription() {
        Product product = Product.create(BRAND_ID, USER_ID, NAME, null, PRICE, ProductStatus.READY);

        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
        assertThat(product.getName()).isEqualTo(NAME);
        assertThat(product.getDescription()).isNull();
        assertThat(product.getPrice()).isEqualTo(PRICE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.READY);
    }

    @Test
    @DisplayName("등록 가능 상태인 READY로 상품을 생성할 수 있다")
    void create_whenStatusIsActive_returnsProductWithActiveStatus() {
        Product product = Product.create(BRAND_ID, USER_ID, NAME, DESCRIPTION, PRICE, ProductStatus.READY);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.READY);
        assertThat(product.getBrandId()).isEqualTo(BRAND_ID);
    }

    @Test
    @DisplayName("옵션 그룹과 값을 포함해 상품을 생성하면 옵션 그래프가 연결된다")
    void create_withOptionGroups_returnsProductWithConnectedOptionGraph() {
        Product product = Product.create(
                BRAND_ID,
                USER_ID,
                NAME,
                DESCRIPTION,
                PRICE,
                ProductStatus.READY,
                List.of(
                        new Product.OptionGroupSpec("색상", 0, true, List.of("Black", "White")),
                        new Product.OptionGroupSpec("사이즈", 1, true, List.of("260", "270"))));

        assertThat(product.getOptionGroups()).hasSize(2);
        assertThat(product.getOptionGroups())
                .extracting(ProductOptionGroup::getName)
                .containsExactly("색상", "사이즈");

        ProductOptionGroup colorGroup = product.getOptionGroups().get(0);
        ProductOptionGroup sizeGroup = product.getOptionGroups().get(1);
        assertThat(colorGroup.getProduct()).isSameAs(product);
        assertThat(sizeGroup.getProduct()).isSameAs(product);

        assertThat(colorGroup.getOptionValues()).hasSize(2);
        assertThat(colorGroup.getOptionValues())
                .extracting(ProductOptionValue::getValue)
                .containsExactly("Black", "White");
        assertThat(colorGroup.getOptionValues())
                .allSatisfy(optionValue -> assertThat(optionValue.getOptionGroup()).isSameAs(colorGroup));

        assertThat(sizeGroup.getOptionValues()).hasSize(2);
        assertThat(sizeGroup.getOptionValues())
                .extracting(ProductOptionValue::getValue)
                .containsExactly("260", "270");
        assertThat(sizeGroup.getOptionValues())
                .allSatisfy(optionValue -> assertThat(optionValue.getOptionGroup()).isSameAs(sizeGroup));
    }

    @Test
    @DisplayName("같은 요청 내 옵션 그룹명이 중복되면 상품 생성에 실패한다")
    void create_whenOptionGroupNameDuplicated_throwsProductOptionGroupNameDuplicatedException() {
        assertThatThrownBy(() -> Product.create(
                BRAND_ID,
                USER_ID,
                NAME,
                DESCRIPTION,
                PRICE,
                ProductStatus.READY,
                List.of(
                        new Product.OptionGroupSpec("색상", 0, true, List.of("Black")),
                        new Product.OptionGroupSpec("색상", 1, true, List.of("White")))))
                .isInstanceOf(ProductOptionGroupNameDuplicatedException.class)
                .satisfies(exception -> {
                    assertThat(((ProductOptionGroupNameDuplicatedException) exception).getCode())
                            .isEqualTo("PRODUCT_OPTION_GROUP_NAME_DUPLICATED");
                    assertThat(((ProductOptionGroupNameDuplicatedException) exception).getHttpStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("옵션 없이 상품을 생성하면 옵션 컬렉션은 비어 있다")
    void create_withoutOptionGroups_returnsProductWithEmptyOptionGroups() {
        Product product = Product.create(
                BRAND_ID,
                USER_ID,
                NAME,
                DESCRIPTION,
                PRICE,
                ProductStatus.READY,
                List.of());

        assertThat(product.getOptionGroups()).isNotNull();
        assertThat(product.getOptionGroups()).isEmpty();
    }
}
