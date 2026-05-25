package com.mist.commerce.domain.brand.dto;

import java.time.OffsetDateTime;

public record BrandCreateResponse(
        Long brandId,
        String name,
        String description,
        Long companyId,
        OffsetDateTime createdAt
) {
}
