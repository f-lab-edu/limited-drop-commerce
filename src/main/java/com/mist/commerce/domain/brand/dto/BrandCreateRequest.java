package com.mist.commerce.domain.brand.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description
) {
}
