package com.mist.commerce.domain.brand.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public record BrandCreateResponse(
        Long brandId,
        String name,
        String description,
        Long companyId,
        LocalDateTime createdAt
) {
}
