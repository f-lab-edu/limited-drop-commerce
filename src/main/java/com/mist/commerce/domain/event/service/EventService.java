package com.mist.commerce.domain.event.service;

import com.mist.commerce.domain.event.dto.EventCreateRequest;
import com.mist.commerce.domain.event.dto.EventCreateResponse;
import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.EventRegistrationForbiddenException;
import com.mist.commerce.domain.event.policy.EventRegistrationPolicy;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private final UserRepository userRepository;
    private final EventRepository dropEventRepository;
    private final EventRegistrationPolicy eventRegistrationPolicy;

    public EventCreateResponse create(Long userId, EventCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EventRegistrationForbiddenException(userId));

        eventRegistrationPolicy.validate(user, request);

        List<EventItem> items = request.items()
                .stream()
                .map(this::toEventItem)
                .toList();

        Event event = Event.create(
                request.brandId(),
                request.title(),
                request.startAt(),
                request.endAt(),
                items
        );

        Event saved = dropEventRepository.save(event);

        return EventCreateResponse
                .builder()
                .eventId(saved.getId())
                .status(saved.getEventStatusName())
                .items(saved.getItems()
                        .stream()
                        .map(item -> new EventCreateResponse.ResponseItem(item.getId(), item.getProductId()))
                        .toList())
                .createdAt(saved.getCreatedAt()
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime())
                .build();
    }

    private EventItem toEventItem(EventCreateRequest.Item item) {
        List<EventItemOptionStock> optionStocks = item.optionStocks() == null
                ? List.of()
                : item.optionStocks()
                        .stream()
                        .map(optionStock -> EventItemOptionStock.create(
                                optionStock.optionGroupId(),
                                optionStock.optionValueId(),
                                optionStock.stockQuantity()
                        ))
                        .toList();
        return EventItem.create(
                item.productId(),
                item.price(),
                item.quantity(),
                item.maxPurchasePerCustomer(),
                optionStocks);
    }
}
