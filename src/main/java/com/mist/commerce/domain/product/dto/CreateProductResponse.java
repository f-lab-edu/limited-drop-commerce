package com.mist.commerce.domain.product.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CreateProductResponse(
        Long productId,
        List<Long> optionGroupIds
) {

    public CreateProductResponse {
        optionGroupIds = optionGroupIds == null ? List.of() : optionGroupIds;
    }
}
