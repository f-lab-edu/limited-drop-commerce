package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.dto.OptionSnapshot;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import com.mist.commerce.domain.reservation.dto.ReserveResult;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationCreator {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final Clock clock;

    public ReserveResult create(ReserveCommand command, ReserveContext context) {
        OptionSnapshot optionSnapshot = context.optionSnapshot();
        LocalDateTime now = LocalDateTime.now(clock);
        OrderItem orderItem = OrderItem.create(
                command.eventItemId(),
                command.eventItemOptionStockId(),
                optionSnapshot.groupName(),
                optionSnapshot.optionName(),
                context.eventItem().getPrice(),
                command.quantity());

        Order saved = orderRepository.save(Order.create(
                command.userId(),
                command.eventId(),
                List.of(orderItem),
                now,
                ReservePolicy.PAYMENT_TTL));

        inventoryReservationRepository.save(InventoryReservation.create(
                saved.getId(),
                command.eventItemId(),
                command.eventItemOptionStockId(),
                command.quantity(),
                now,
                ReservePolicy.PAYMENT_TTL));

        ReserveResult result = new ReserveResult(saved.getId(), saved.getExpiresAt(), saved.getStatus().name());
        return result;
    }
}
