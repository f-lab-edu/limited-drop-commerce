package com.mist.commerce.domain.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventCreateRequest(
        @NotNull Long brandId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull Long productId,
            @NotNull @PositiveOrZero BigDecimal price,
            @Min(1) int quantity,
            @Min(1) int maxPurchasePerCustomer,
            @Valid List<OptionStock> optionStocks
    ) {
    }

    public record OptionStock(
            @NotNull Long optionGroupId,
            @NotNull Long optionValueId,
            @Min(0) int stockQuantity
    ) {
    }
}
