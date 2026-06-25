package com.mist.commerce.domain.order.service;

import com.mist.commerce.domain.event.exception.EventItemOptionNotFoundException;
import com.mist.commerce.domain.event.repository.EventItemOptionStockRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderAlreadyCancelledException;
import com.mist.commerce.domain.order.exception.OrderCancelTemporarilyUnavailableException;
import com.mist.commerce.domain.order.exception.OrderCannotCancelException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.entity.ReservationStatus;
import com.mist.commerce.domain.reservation.exception.IdempotencyKeyReusedException;
import com.mist.commerce.domain.reservation.exception.ReservationInProgressException;
import com.mist.commerce.domain.reservation.redis.ClaimResult;
import com.mist.commerce.domain.reservation.redis.ClaimStatus;
import com.mist.commerce.domain.reservation.redis.IdempotencyRedisRepository;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.redis.ReservationExpiryRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import java.time.Clock;
import java.time.Duration;
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
public class OrderCancelService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);

    private final OrderRepository orderRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final EventItemOptionStockRepository eventItemOptionStockRepository;
    private final OptionStockRedisRepository optionStockRedisRepository;
    private final ReservationExpiryRedisRepository reservationExpiryRedisRepository;
    private final IdempotencyRedisRepository idempotencyRedisRepository;
    private final Clock clock;

    @Transactional
    public CancelResult cancel(CancelCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(OrderNotFoundException::new);
        validateOwner(order, command.userId());
        validateCancellable(order.getStatus());

        String fingerprint = fingerprint(command);
        ClaimResult claimResult = idempotencyRedisRepository.claim(
                command.userId(),
                command.idempotencyKey(),
                fingerprint,
                IDEMPOTENCY_TTL);
        if (claimResult.status() == ClaimStatus.COMPLETED) {
            return deserializeResult(claimResult.resultPayload());
        }
        if (claimResult.status() == ClaimStatus.MISMATCH) {
            throw new IdempotencyKeyReusedException();
        }
        if (claimResult.status() == ClaimStatus.IN_PROGRESS) {
            throw new ReservationInProgressException();
        }

        CancelResult[] resultHolder = new CancelResult[1];
        boolean idempotencySynchronizationRegistered = false;

        try {
            LocalDateTime now = LocalDateTime.now(clock);
            if (orderRepository.cancelIfPending(
                    command.orderId(),
                    now,
                    OrderStatus.PENDING_PAYMENT,
                    OrderStatus.CANCELLED) == 0) {
                throw mapReloadedState(command.orderId());
            }

            List<StockRestore> restores = restoreDbStock(command.orderId(), now);
            CancelResult result = new CancelResult(command.orderId(), OrderStatus.CANCELLED.name(), now);
            resultHolder[0] = result;
            registerRedisRestoreAfterCommit(command.orderId(), restores);
            idempotencySynchronizationRegistered =
                    registerIdempotencySynchronization(command, fingerprint, resultHolder);
            if (!idempotencySynchronizationRegistered) {
                idempotencyRedisRepository.complete(
                        command.userId(),
                        command.idempotencyKey(),
                        fingerprint,
                        serializeResult(result));
            }
            return result;
        } catch (RuntimeException ex) {
            if (!idempotencySynchronizationRegistered) {
                idempotencyRedisRepository.release(command.userId(), command.idempotencyKey());
            }
            throw ex;
        }
    }

    private void validateOwner(Order order, Long userId) {
        if (!order.getUserId().equals(userId)) {
            throw new OrderForbiddenException();
        }
    }

    private void validateCancellable(OrderStatus status) {
        if (status == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException();
        }
        if (status == OrderStatus.PAID || status == OrderStatus.EXPIRED || status == OrderStatus.PAYMENT_FAILED) {
            throw new OrderCannotCancelException();
        }
    }

    private RuntimeException mapReloadedState(Long orderId) {
        Order reloaded = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (reloaded.getStatus() == OrderStatus.CANCELLED) {
            return new OrderAlreadyCancelledException();
        }
        if (reloaded.getStatus() == OrderStatus.PAID
                || reloaded.getStatus() == OrderStatus.EXPIRED
                || reloaded.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return new OrderCannotCancelException();
        }
        return new OrderCancelTemporarilyUnavailableException();
    }

    private List<StockRestore> restoreDbStock(Long orderId, LocalDateTime now) {
        List<StockRestore> restores = new ArrayList<>();
        for (InventoryReservation reservation : inventoryReservationRepository.findByOrderId(orderId)) {
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                continue;
            }

            eventItemOptionStockRepository.findById(reservation.getEventItemOptionId())
                    .orElseThrow(EventItemOptionNotFoundException::new)
                    .release(reservation.getReservedQuantity());
            reservation.release(now);
            restores.add(new StockRestore(reservation.getEventItemOptionId(), reservation.getReservedQuantity()));
        }
        return restores;
    }

    private boolean registerIdempotencySynchronization(
            CancelCommand command,
            String fingerprint,
            CancelResult[] resultHolder
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CancelResult result = resultHolder[0];
                if (result != null) {
                    idempotencyRedisRepository.complete(
                            command.userId(),
                            command.idempotencyKey(),
                            fingerprint,
                            serializeResult(result));
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    idempotencyRedisRepository.release(command.userId(), command.idempotencyKey());
                }
            }
        });
        return true;
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

    private String fingerprint(CancelCommand command) {
        return "orderId=" + command.orderId();
    }

    private String serializeResult(CancelResult result) {
        return "{\"orderId\":" + result.orderId()
                + ",\"status\":\"" + result.status()
                + "\",\"cancelledAt\":\"" + result.cancelledAt()
                + "\"}";
    }

    private CancelResult deserializeResult(String payload) {
        Long orderId = Long.valueOf(extractJsonValue(payload, "\"orderId\":", ",\"status\""));
        String status = extractJsonValue(payload, "\"status\":\"", "\",\"cancelledAt\"");
        LocalDateTime cancelledAt = LocalDateTime.parse(extractJsonValue(payload, "\"cancelledAt\":\"", "\"}"));
        return new CancelResult(orderId, status, cancelledAt);
    }

    private String extractJsonValue(String payload, String prefix, String suffix) {
        int start = payload.indexOf(prefix);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid order cancel result payload.");
        }
        start += prefix.length();
        int end = payload.indexOf(suffix, start);
        if (end < 0) {
            throw new IllegalArgumentException("Invalid order cancel result payload.");
        }
        return payload.substring(start, end);
    }

    private record StockRestore(Long optionStockId, int quantity) {
    }
}
