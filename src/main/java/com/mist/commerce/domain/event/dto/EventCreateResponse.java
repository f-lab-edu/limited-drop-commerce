package com.mist.commerce.domain.event.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

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
