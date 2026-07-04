package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.domain.reservation.dto.ReserveContext;
import com.mist.commerce.domain.reservation.exception.ActiveReservationAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationValidator {

    private final OrderRepository orderRepository;

    public void validate(ReserveCommand command, ReserveContext context) {
        int alreadyPurchasedQuantity = 0;
        context.eventItem().verifyPurchasableQuantity(command.quantity(), alreadyPurchasedQuantity);
        context.event().verifyParticipable();
        validateNoActiveReservation(command);
    }

    private void validateNoActiveReservation(ReserveCommand command) {
        boolean exists = orderRepository.existsByUserIdAndEventIdAndStatus(
                command.userId(),
                command.eventId(),
                OrderStatus.PENDING_PAYMENT
        );

        if (exists) {
            throw new ActiveReservationAlreadyExistsException();
        }
    }
}
