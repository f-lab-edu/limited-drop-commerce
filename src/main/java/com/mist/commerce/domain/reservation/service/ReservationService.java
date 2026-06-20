package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.DropEventNotFoundException;
import com.mist.commerce.domain.event.exception.EventItemOptionNotFoundException;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.exception.ActiveReservationAlreadyExistsException;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final Clock clock;

    public ReservationService(
            EventRepository eventRepository,
            OrderRepository orderRepository,
            InventoryReservationRepository inventoryReservationRepository,
            ProductOptionGroupRepository productOptionGroupRepository,
            ProductOptionValueRepository productOptionValueRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.productOptionGroupRepository = productOptionGroupRepository;
        this.productOptionValueRepository = productOptionValueRepository;
        this.clock = clock;
    }

    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        Event event = eventRepository.findById(command.eventId())
                .orElseThrow(DropEventNotFoundException::new);

        event.verifyParticipable();

        if (orderRepository.existsByUserIdAndEventIdAndStatus(
                command.userId(),
                command.eventId(),
                OrderStatus.PENDING_PAYMENT)) {
            throw new ActiveReservationAlreadyExistsException();
        }

        EventItem eventItem = event.getItems().stream()
                .filter(item -> item.getId().equals(command.eventItemId()))
                .findFirst()
                .orElseThrow(EventItemOptionNotFoundException::new);

        EventItemOptionStock optionStock = eventItem.getOptionStocks().stream()
                .filter(stock -> stock.getId().equals(command.eventItemOptionStockId()))
                .findFirst()
                .orElseThrow(EventItemOptionNotFoundException::new);

        eventItem.verifyPurchasableQuantity(command.quantity(), 0);
        optionStock.reserve(command.quantity());

        String groupName = productOptionGroupRepository.findById(optionStock.getProductOptionGroupId())
                .orElseThrow(EventItemOptionNotFoundException::new)
                .getName();
        String valueName = productOptionValueRepository.findById(optionStock.getProductOptionValueId())
                .orElseThrow(EventItemOptionNotFoundException::new)
                .getValue();

        LocalDateTime now = LocalDateTime.now(clock);
        OrderItem orderItem = OrderItem.create(
                command.eventItemId(),
                command.eventItemOptionStockId(),
                groupName,
                valueName,
                eventItem.getPrice(),
                command.quantity());

        Order saved = orderRepository.save(Order.create(
                command.userId(),
                command.eventId(),
                List.of(orderItem),
                now,
                PAYMENT_TTL));

        inventoryReservationRepository.save(InventoryReservation.create(
                saved.getId(),
                command.eventItemId(),
                command.eventItemOptionStockId(),
                command.quantity(),
                now,
                PAYMENT_TTL));

        return new ReserveResult(saved.getId(), saved.getExpiresAt(), saved.getStatus().name());
    }
}
