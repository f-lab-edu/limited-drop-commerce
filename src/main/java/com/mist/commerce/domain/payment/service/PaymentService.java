package com.mist.commerce.domain.payment.service;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderCannotPayException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.payment.entity.Payment;
import com.mist.commerce.domain.payment.entity.PaymentStatus;
import com.mist.commerce.domain.payment.entity.PaymentTransaction;
import com.mist.commerce.domain.payment.entity.TransactionStatus;
import com.mist.commerce.domain.payment.entity.TransactionType;
import com.mist.commerce.domain.payment.event.PaymentCompletedEvent;
import com.mist.commerce.domain.payment.event.PaymentEventPublisher;
import com.mist.commerce.domain.payment.exception.PaymentAmountMismatchException;
import com.mist.commerce.domain.payment.exception.PaymentFailedException;
import com.mist.commerce.domain.payment.gateway.PaymentApproval;
import com.mist.commerce.domain.payment.gateway.PaymentApprovalCommand;
import com.mist.commerce.domain.payment.gateway.PaymentGateway;
import com.mist.commerce.domain.payment.repository.PaymentRepository;
import com.mist.commerce.domain.payment.repository.PaymentTransactionRepository;
import com.mist.commerce.domain.reservation.exception.IdempotencyKeyReusedException;
import com.mist.commerce.domain.reservation.exception.ReservationInProgressException;
import com.mist.commerce.domain.reservation.redis.ClaimResult;
import com.mist.commerce.domain.reservation.redis.ClaimStatus;
import com.mist.commerce.domain.reservation.redis.IdempotencyRedisRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentEventPublisher eventPublisher;
    private final IdempotencyRedisRepository idempotencyRedisRepository;
    private final Clock clock;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentGateway paymentGateway,
            PaymentEventPublisher eventPublisher,
            IdempotencyRedisRepository idempotencyRedisRepository,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
        this.idempotencyRedisRepository = idempotencyRedisRepository;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResult pay(PaymentCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(OrderNotFoundException::new);
        validateOwner(order, command.userId());
        validatePayable(order.getStatus());
        validateAmount(order.getTotalAmount(), command.amount());

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

        boolean idempotencySynchronizationRegistered = false;

        try {
            LocalDateTime now = LocalDateTime.now(clock);
            Payment payment = Payment.create(
                    generatePaymentNo(now),
                    command.userId(),
                    command.orderId(),
                    command.amount(),
                    now,
                    command.idempotencyKey());
            payment = paymentRepository.save(payment);

            PaymentApproval approval;
            try {
                approval = paymentGateway.approve(new PaymentApprovalCommand(
                        command.paymentKey(),
                        payment.getPaymentNo(),
                        command.amount()));
                if (!approval.isApproved()) {
                    throw new PaymentFailedException();
                }
            } catch (PaymentFailedException ex) {
                payment.fail(now);
                paymentRepository.save(payment);
                idempotencyRedisRepository.release(command.userId(), command.idempotencyKey());
                throw ex;
            }

            payment.approve(now);
            payment = paymentRepository.save(payment);

            PaymentResult result = new PaymentResult(
                    payment.getId(),
                    payment.getPaymentNo(),
                    OrderStatus.PAID.name(),
                    PaymentStatus.APPROVED.name());
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    command.orderId(),
                    payment.getId(),
                    command.userId(),
                    command.amount(),
                    now);
            idempotencySynchronizationRegistered =
                    registerIdempotencySynchronization(command, fingerprint, result, event);
            if (!idempotencySynchronizationRegistered) {
                publishAndComplete(command, fingerprint, result, event);
            }

            saveTransactions(payment.getId(), approval);
            order.markPaid();
            return result;
        } catch (RuntimeException ex) {
            if (!idempotencySynchronizationRegistered && !(ex instanceof PaymentFailedException)) {
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

    private void validatePayable(OrderStatus status) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new OrderCannotPayException();
        }
    }

    private void validateAmount(BigDecimal expectedAmount, BigDecimal requestedAmount) {
        if (expectedAmount.compareTo(requestedAmount) != 0) {
            throw new PaymentAmountMismatchException();
        }
    }

    private void saveTransactions(Long paymentId, PaymentApproval approval) {
        paymentTransactionRepository.save(PaymentTransaction.create(
                paymentId,
                TransactionType.REQUEST,
                null,
                approval.getRequestPayload(),
                null,
                TransactionStatus.SUCCESS));
        paymentTransactionRepository.save(PaymentTransaction.create(
                paymentId,
                TransactionType.APPROVE,
                approval.getExternalTransactionId(),
                null,
                approval.getResponsePayload(),
                TransactionStatus.SUCCESS));
    }

    private boolean registerIdempotencySynchronization(
            PaymentCommand command,
            String fingerprint,
            PaymentResult result,
            PaymentCompletedEvent event
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishAndComplete(command, fingerprint, result, event);
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

    private void publishAndComplete(
            PaymentCommand command,
            String fingerprint,
            PaymentResult result,
            PaymentCompletedEvent event
    ) {
        eventPublisher.publishPaymentCompleted(event);
        idempotencyRedisRepository.complete(
                command.userId(),
                command.idempotencyKey(),
                fingerprint,
                serializeResult(result));
    }

    private String fingerprint(PaymentCommand command) {
        return "orderId=" + command.orderId();
    }

    private String generatePaymentNo(LocalDateTime now) {
        return "PAY-" + PAYMENT_NO_DATE_FORMATTER.format(now) + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String serializeResult(PaymentResult result) {
        return "{\"paymentId\":" + result.paymentId()
                + ",\"paymentNo\":\"" + result.paymentNo()
                + "\",\"orderStatus\":\"" + result.orderStatus()
                + "\",\"paymentStatus\":\"" + result.paymentStatus()
                + "\"}";
    }

    private PaymentResult deserializeResult(String payload) {
        Long paymentId = Long.valueOf(extractJsonValue(payload, "\"paymentId\":", ",\"paymentNo\""));
        String paymentNo = extractJsonValue(payload, "\"paymentNo\":\"", "\",\"orderStatus\"");
        String orderStatus = extractJsonValue(payload, "\"orderStatus\":\"", "\",\"paymentStatus\"");
        String paymentStatus = extractJsonValue(payload, "\"paymentStatus\":\"", "\"}");
        return new PaymentResult(paymentId, paymentNo, orderStatus, paymentStatus);
    }

    private String extractJsonValue(String payload, String prefix, String suffix) {
        int start = payload.indexOf(prefix);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid payment result payload.");
        }
        start += prefix.length();
        int end = payload.indexOf(suffix, start);
        if (end < 0) {
            throw new IllegalArgumentException("Invalid payment result payload.");
        }
        return payload.substring(start, end);
    }
}
