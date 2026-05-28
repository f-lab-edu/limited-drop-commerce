package com.mist.commerce.domain.product.dto;

import com.mist.commerce.domain.product.entity.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateProductRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품명은 필수입니다.")
        String name,

        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Long price,

        @NotNull(message = "상품 상태는 필수입니다.")
        ProductStatus status,

        @Valid
        List<OptionGroupRequest> optionGroups
) {

    public record OptionGroupRequest(
            @NotBlank
            @Size(max = 50)
            String name,

            @NotNull
            @Min(0)
            Integer displayOrder,

            @NotNull
            Boolean required,

            @NotEmpty
            @Valid
            List<OptionValueRequest> values
    ) {
    }

    public record OptionValueRequest(
            @NotBlank
            @Size(max = 20)
            String value
    ) {
    }
}
