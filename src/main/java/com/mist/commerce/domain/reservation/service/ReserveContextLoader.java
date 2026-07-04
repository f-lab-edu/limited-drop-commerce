package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.EventNotFoundException;
import com.mist.commerce.domain.event.exception.EventItemOptionNotFoundException;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.reservation.dto.OptionSnapshot;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReserveContextLoader {

    private final EventRepository eventRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductOptionValueRepository productOptionValueRepository;

    public ReserveContext load(ReserveCommand command) {
        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(EventNotFoundException::new);

        EventItem eventItem = event.getEventItem(command.eventItemId());
        EventItemOptionStock optionStock = eventItem.getOptionStock(command.eventItemOptionStockId());
        OptionSnapshot optionSnapshot = loadOptionSnapshot(optionStock);

        return ReserveContext.builder()
                .event(event)
                .eventItem(eventItem)
                .eventItemOptionStock(optionStock)
                .optionSnapshot(optionSnapshot)
                .build();
    }

    private OptionSnapshot loadOptionSnapshot(EventItemOptionStock optionStock) {

        String groupName = productOptionGroupRepository
                .findById(optionStock.getProductOptionGroupId())
                .orElseThrow(EventItemOptionNotFoundException::new)
                .getName();

        String valueName = productOptionValueRepository
                .findById(optionStock.getProductOptionValueId())
                .orElseThrow(EventItemOptionNotFoundException::new)
                .getValue();

        return new OptionSnapshot(groupName, valueName);

    }
}
