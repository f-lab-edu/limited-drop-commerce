package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.exception.EventItemOptionNotFoundException;
import com.mist.commerce.domain.event.repository.EventItemOptionStockRepository;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.redis.ReservationExpiryRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ExpiryRecoveryService {

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final EventItemOptionStockRepository eventItemOptionStockRepository;
    private final OptionStockRedisRepository optionStockRedisRepository;
    private final ReservationExpiryRedisRepository reservationExpiryRedisRepository;
    private final Clock clock;

    @Transactional
    public void recover(Long orderId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (orderRepository.expireIfPending(
                orderId,
                now,
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.EXPIRED) == 0) {
            return;
        }

        List<StockRestore> restores = new ArrayList<>();
        for (InventoryReservation reservation : inventoryReservationRepository.findByOrderId(orderId)) {
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            eventItemOptionStockRepository.findById(reservation.getEventItemOptionId())
                    .orElseThrow(EventItemOptionNotFoundException::new)
                    .release(reservation.getReservedQuantity());
            reservation.expire();
            reservation.release(now);
            restores.add(new StockRestore(reservation.getEventItemOptionId(), reservation.getReservedQuantity()));
        }

        registerRedisRestoreAfterCommit(orderId, restores);
    }

    private void registerRedisRestoreAfterCommit(Long orderId, List<StockRestore> restores) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            applyRedisRestore(orderId, restores);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applyRedisRestore(orderId, restores);
            }
        });
    }

    private void applyRedisRestore(Long orderId, List<StockRestore> restores) {
        for (StockRestore restore : restores) {
            optionStockRedisRepository.increase(restore.optionStockId(), restore.quantity());
        }
        reservationExpiryRedisRepository.clearExpiry(orderId);
    }

    private record StockRestore(Long optionStockId, int quantity) {
    }
}
