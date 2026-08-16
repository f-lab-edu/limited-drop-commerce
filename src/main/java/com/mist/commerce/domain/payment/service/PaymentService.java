package com.mist.commerce.domain.payment.service;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderCannotPayException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.payment.dto.PaymentCommand;
import com.mist.commerce.domain.payment.dto.PaymentResult;
import com.mist.commerce.domain.payment.entity.Payment;
import com.mist.commerce.domain.payment.entity.PaymentStatus;
import com.mist.commerce.domain.payment.entity.PaymentTransaction;
import com.mist.commerce.domain.payment.entity.TransactionStatus;
import com.mist.commerce.domain.payment.entity.TransactionType;
import com.mist.commerce.domain.payment.exception.PaymentAmountMismatchException;
import com.mist.commerce.domain.payment.exception.PaymentFailedException;
import com.mist.commerce.domain.payment.gateway.PaymentApproval;
import com.mist.commerce.domain.payment.gateway.PaymentApprovalCommand;
import com.mist.commerce.domain.payment.gateway.PaymentGateway;
import com.mist.commerce.domain.payment.repository.PaymentRepository;
import com.mist.commerce.domain.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter PAYMENT_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGateway paymentGateway;

    private final Clock clock;

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResult pay(PaymentCommand command) {
        Order order = orderRepository.findById(command.orderId())
                .orElseThrow(OrderNotFoundException::new);

        validateOwner(order, command.userId());
        validatePayable(order.getStatus());
        validateAmount(order.getTotalAmount(), command.amount());

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
                throw ex;
            }

            payment.approve(now);
            payment = paymentRepository.save(payment);

            PaymentResult result = new PaymentResult(
                    payment.getId(),
                    payment.getPaymentNo(),
                    OrderStatus.PAID.name(),
                    PaymentStatus.APPROVED.name());

            saveTransactions(payment.getId(), approval);
            order.markPaid();
            return result;
        } catch (RuntimeException ex) {
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

    private String generatePaymentNo(LocalDateTime now) {
        return "PAY-" + PAYMENT_NO_DATE_FORMATTER.format(now) + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
