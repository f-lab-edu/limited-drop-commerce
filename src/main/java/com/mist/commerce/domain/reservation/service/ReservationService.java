package com.mist.commerce.domain.reservation.service;

import com.mist.commerce.domain.event.entity.Event;
import com.mist.commerce.domain.event.entity.EventItem;
import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import com.mist.commerce.domain.event.exception.DropEventNotFoundException;
import com.mist.commerce.domain.event.exception.EventItemOptionNotFoundException;
import com.mist.commerce.domain.event.exception.InsufficientStockException;
import com.mist.commerce.domain.event.exception.StockExhaustedException;
import com.mist.commerce.domain.event.repository.EventRepository;
import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.product.repository.ProductOptionGroupRepository;
import com.mist.commerce.domain.product.repository.ProductOptionValueRepository;
import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.domain.reservation.exception.ActiveReservationAlreadyExistsException;
import com.mist.commerce.domain.reservation.exception.IdempotencyKeyReusedException;
import com.mist.commerce.domain.reservation.exception.ReservationInProgressException;
import com.mist.commerce.domain.reservation.redis.ClaimResult;
import com.mist.commerce.domain.reservation.redis.ClaimStatus;
import com.mist.commerce.domain.reservation.redis.IdempotencyRedisRepository;
import com.mist.commerce.domain.reservation.redis.OptionStockRedisRepository;
import com.mist.commerce.domain.reservation.repository.InventoryReservationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReservationService {

    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);

    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final ProductOptionGroupRepository productOptionGroupRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final OptionStockRedisRepository optionStockRedisRepository;
    private final IdempotencyRedisRepository idempotencyRedisRepository;
    private final Clock clock;

    public ReservationService(
            EventRepository eventRepository,
            OrderRepository orderRepository,
            InventoryReservationRepository inventoryReservationRepository,
            ProductOptionGroupRepository productOptionGroupRepository,
            ProductOptionValueRepository productOptionValueRepository,
            OptionStockRedisRepository optionStockRedisRepository,
            IdempotencyRedisRepository idempotencyRedisRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.inventoryReservationRepository = inventoryReservationRepository;
        this.productOptionGroupRepository = productOptionGroupRepository;
        this.productOptionValueRepository = productOptionValueRepository;
        this.optionStockRedisRepository = optionStockRedisRepository;
        this.idempotencyRedisRepository = idempotencyRedisRepository;
        this.clock = clock;
    }

    @Transactional
    public ReserveResult reserve(ReserveCommand command) {
        String fingerprint = fingerprint(command);
        ClaimResult claimResult = idempotencyRedisRepository.claim(
                command.userId(),
                command.idempotencyKey(),
                fingerprint,
                PAYMENT_TTL);
        if (claimResult.status() == ClaimStatus.COMPLETED) {
            return deserializeResult(claimResult.resultPayload());
        }
        if (claimResult.status() == ClaimStatus.MISMATCH) {
            throw new IdempotencyKeyReusedException();
        }
        if (claimResult.status() == ClaimStatus.IN_PROGRESS) {
            throw new ReservationInProgressException();
        }

        ReserveResult[] resultHolder = new ReserveResult[1];
        registerIdempotencySynchronization(command.userId(), command.idempotencyKey(), fingerprint, resultHolder);

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

        int dbAvailable = optionStock.getStockQuantity() - optionStock.getReservedQuantity();
        long remaining = optionStockRedisRepository.tryDecrease(
                command.eventItemOptionStockId(),
                command.quantity(),
                dbAvailable);
        if (remaining < 0) {
            throw dbAvailable <= 0 ? new StockExhaustedException() : new InsufficientStockException();
        }
        registerRedisCompensation(command.eventItemOptionStockId(), command.quantity());

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

        ReserveResult result = new ReserveResult(saved.getId(), saved.getExpiresAt(), saved.getStatus().name());
        resultHolder[0] = result;
        return result;
    }

    private void registerIdempotencySynchronization(
            Long userId,
            String idempotencyKey,
            String fingerprint,
            ReserveResult[] resultHolder
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ReserveResult result = resultHolder[0];
                if (result != null) {
                    idempotencyRedisRepository.complete(userId, idempotencyKey, fingerprint, serializeResult(result));
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    idempotencyRedisRepository.release(userId, idempotencyKey);
                }
            }
        });
    }

    private void registerRedisCompensation(Long optionStockId, int quantity) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    optionStockRedisRepository.increase(optionStockId, quantity);
                }
            }
        });
    }

    private String fingerprint(ReserveCommand command) {
        String canonical = "eventId=" + command.eventId()
                + ";eventItemId=" + command.eventItemId()
                + ";eventItemOptionStockId=" + command.eventItemOptionStockId()
                + ";quantity=" + command.quantity();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", ex);
        }
    }

    private String serializeResult(ReserveResult result) {
        return "{\"orderId\":" + result.orderId()
                + ",\"expiresAt\":\"" + result.expiresAt()
                + "\",\"status\":\"" + result.status()
                + "\"}";
    }

    private ReserveResult deserializeResult(String payload) {
        Long orderId = Long.valueOf(extractJsonValue(payload, "\"orderId\":", ",\"expiresAt\""));
        LocalDateTime expiresAt = LocalDateTime.parse(extractJsonValue(payload, "\"expiresAt\":\"", "\",\"status\""));
        String status = extractJsonValue(payload, "\"status\":\"", "\"}");
        return new ReserveResult(orderId, expiresAt, status);
    }

    private String extractJsonValue(String payload, String prefix, String suffix) {
        int start = payload.indexOf(prefix);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid reservation result payload.");
        }
        start += prefix.length();
        int end = payload.indexOf(suffix, start);
        if (end < 0) {
            throw new IllegalArgumentException("Invalid reservation result payload.");
        }
        return payload.substring(start, end);
    }
}
