package com.mist.commerce.domain.event.dto;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record EventCreateResponse(
        Long eventId,
        String status,
        List<ResponseItem> items,
        OffsetDateTime createdAt
) {
    public record ResponseItem(
            Long eventItemId,
            Long productId
    ) {
    }
}
