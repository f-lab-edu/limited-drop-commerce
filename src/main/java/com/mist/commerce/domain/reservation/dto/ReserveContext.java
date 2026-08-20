package com.mist.commerce.domain.reservation.dto;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import lombok.Builder;

@Builder
public record ReserveContext (
    Event event,
    EventItem eventItem,
    EventItemOptionStock eventItemOptionStock,
    OptionSnapshot optionSnapshot
) {

}
