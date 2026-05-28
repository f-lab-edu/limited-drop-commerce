package com.mist.commerce.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.domain.product.exception.ProductOptionValueDuplicatedException;
import com.mist.commerce.domain.product.exception.ProductOptionValueRequiredException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductOptionGroupTest {

    @Test
    @DisplayName("옵션 값이 없으면 옵션 그룹 생성에 실패한다")
    void create_whenValuesAreEmpty_throwsProductOptionValueRequiredException() {
        assertThatThrownBy(() -> ProductOptionGroup.create("색상", 0, true, List.of()))
                .isInstanceOf(ProductOptionValueRequiredException.class)
                .satisfies(exception -> {
                    assertThat(((ProductOptionValueRequiredException) exception).getCode())
                            .isEqualTo("PRODUCT_OPTION_VALUE_REQUIRED");
                    assertThat(((ProductOptionValueRequiredException) exception).getHttpStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("같은 그룹 내 옵션 값이 중복되면 옵션 그룹 생성에 실패한다")
    void create_whenValueDuplicated_throwsProductOptionValueDuplicatedException() {
        assertThatThrownBy(() -> ProductOptionGroup.create("색상", 0, true, List.of("Black", "Black")))
                .isInstanceOf(ProductOptionValueDuplicatedException.class)
                .satisfies(exception -> {
                    assertThat(((ProductOptionValueDuplicatedException) exception).getCode())
                            .isEqualTo("PRODUCT_OPTION_VALUE_DUPLICATED");
                    assertThat(((ProductOptionValueDuplicatedException) exception).getHttpStatus())
                            .isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("옵션 그룹명이 blank이면 옵션 그룹 생성에 실패한다")
    void create_whenNameIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ProductOptionGroup.create("   ", 0, true, List.of("Black")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("옵션 값이 blank이면 옵션 그룹 생성에 실패한다")
    void create_whenValueIsBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ProductOptionGroup.create("색상", 0, true, List.of("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    @DisplayName("노출 순서가 음수이면 옵션 그룹 생성에 실패한다")
    void create_whenDisplayOrderIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ProductOptionGroup.create("색상", -1, true, List.of("Black")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayOrder");
    }
}
