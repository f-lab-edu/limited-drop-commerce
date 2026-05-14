package com.mist.commerce.domain.brand.dto;

import java.time.Instant;

public record BrandCreateResponse(
        Long brandId,
        String name,
        String description,
        Long companyId,
        Instant createdAt
) {
}
