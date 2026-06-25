package com.mist.commerce.domain.reservation.scheduler;

import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.service.ExpiryRecoveryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredOrderScheduler {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final ExpiryRecoveryService expiryRecoveryService;
    private final Clock clock;

    @Scheduled(fixedDelay = 30_000)
    public void recoverExpiredOrders() {
        List<Long> ids = orderRepository.findExpiredPendingPaymentIds(
                OrderStatus.PENDING_PAYMENT,
                LocalDateTime.now(clock),
                PageRequest.of(0, BATCH_SIZE));

        for (Long orderId : ids) {
            try {
                expiryRecoveryService.recover(orderId);
            } catch (Exception e) {
                log.warn("Failed to recover expired order. orderId={}", orderId, e);
            }
        }
    }
}
