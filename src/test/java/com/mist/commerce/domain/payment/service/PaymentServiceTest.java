package com.mist.commerce.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderItem;
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
import com.mist.commerce.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    private static final Long ORDER_ID = 100L;
    private static final Long EVENT_ID = 200L;
    private static final Long EVENT_ITEM_ID = 300L;
    private static final Long OPTION_STOCK_ID = 400L;
    private static final Long PAYMENT_ID = 500L;
    private static final int QUANTITY = 3;
    private static final String PAYMENT_KEY = "pay_key_001";
    private static final String IDEMPOTENCY_KEY = "idem-pay-001";
    private static final String PAYMENT_NO = "PAY-20260627-0001";
    private static final String EXTERNAL_TRANSACTION_ID = "toss-tx-001";
    private static final String REQUEST_PAYLOAD =
            "{\"paymentKey\":\"pay_key_001\",\"orderId\":\"PAY-20260627-0001\",\"amount\":150000.00}";
    private static final String RESPONSE_PAYLOAD =
            "{\"paymentKey\":\"pay_key_001\",\"status\":\"DONE\",\"totalAmount\":150000.00}";
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final BigDecimal MISMATCHED_AMOUNT = new BigDecimal("149999.00");
    private static final Duration IDEMPOTENCY_TTL = Duration.ofMinutes(30);
    private static final Duration PAYMENT_TTL = Duration.ofMinutes(30);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-27T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final String COMPLETED_PAYMENT_PAYLOAD =
            "{\"paymentId\":500,\"paymentNo\":\"PAY-20260627-0001\",\"orderStatus\":\"PAID\","
                    + "\"paymentStatus\":\"APPROVED\"}";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @Mock
    private IdempotencyRedisRepository idempotencyRedisRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.CLAIMED, null));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
            ReflectionTestUtils.setField(payment, "paymentNo", PAYMENT_NO);
            return payment;
        });
        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(paymentGateway.approve(any(PaymentApprovalCommand.class)))
                .thenReturn(paymentApproval());

        paymentService = new PaymentService(
                orderRepository,
                paymentRepository,
                paymentTransactionRepository,
                paymentGateway,
                eventPublisher,
                idempotencyRedisRepository,
                CLOCK);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("TC-PAY-SVC-001: 결제 성공 시 Payment 승인, 주문 PAID, 트랜잭션 2행 저장, afterCommit 이벤트 발행")
    void pay_whenPaymentApproved_approvesPaymentMarksOrderPaidSavesTransactionsAndPublishesAfterCommit() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        TransactionSynchronizationManager.initSynchronization();

        PaymentResult result = paymentService.pay(command());

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.paymentNo()).isEqualTo(PAYMENT_NO);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED.name());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getAllValues().get(paymentCaptor.getAllValues().size() - 1);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(savedPayment.getApprovedAt()).isEqualTo(NOW);
        assertThat(savedPayment.getUserId()).isEqualTo(USER_ID);
        assertThat(savedPayment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(savedPayment.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(savedPayment.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        verify(idempotencyRedisRepository).claim(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), eq(IDEMPOTENCY_TTL));

        ArgumentCaptor<PaymentApprovalCommand> approvalCommandCaptor =
                ArgumentCaptor.forClass(PaymentApprovalCommand.class);
        verify(paymentGateway).approve(approvalCommandCaptor.capture());
        PaymentApprovalCommand approvalCommand = approvalCommandCaptor.getValue();
        assertThat(approvalCommand.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(approvalCommand.orderId()).isEqualTo(PAYMENT_NO);
        assertThat(approvalCommand.amount()).isEqualByComparingTo(AMOUNT);

        ArgumentCaptor<PaymentTransaction> transactionCaptor =
                ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository, org.mockito.Mockito.times(2)).save(transactionCaptor.capture());
        List<PaymentTransaction> transactions = transactionCaptor.getAllValues();
        assertRequestTransaction(transactions.get(0));
        assertApproveTransaction(transactions.get(1));

        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());

        triggerAfterCommit();

        ArgumentCaptor<PaymentCompletedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(eventPublisher).publishPaymentCompleted(eventCaptor.capture());
        PaymentCompletedEvent event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(event.occurredAt()).isEqualTo(NOW);

        verify(idempotencyRedisRepository).complete(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), any());
        InOrder afterCommitOrder = inOrder(eventPublisher, idempotencyRedisRepository);
        afterCommitOrder.verify(eventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
        afterCommitOrder.verify(idempotencyRedisRepository).complete(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), any());
        verify(idempotencyRedisRepository, never()).release(any(), any());
    }

    @Test
    @DisplayName("TC-PAY-SVC-002: 금액 불일치 시 PAYMENT_AMOUNT_MISMATCH, claim 전 중단, 부작용 없음")
    void pay_whenAmountMismatched_throwsPaymentAmountMismatchBeforeClaimWithoutSideEffects() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertBusinessException(
                PaymentAmountMismatchException.class,
                "PAYMENT_AMOUNT_MISMATCH",
                () -> paymentService.pay(command(MISMATCHED_AMOUNT, "idem-pay-amount-mismatch")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verifyNoPaymentSideEffects();
    }

    @Test
    @DisplayName("TC-PAY-SVC-003: 주문 미존재 시 ORDER_NOT_FOUND, claim 전 중단, 부작용 없음")
    void pay_whenOrderDoesNotExist_throwsOrderNotFoundBeforeClaimWithoutSideEffects() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertBusinessException(OrderNotFoundException.class, "ORDER_NOT_FOUND", () -> paymentService.pay(command()));

        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verifyNoPaymentSideEffects();
    }

    @Test
    @DisplayName("TC-PAY-SVC-004: 본인 주문이 아니면 ORDER_FORBIDDEN, claim 전 중단, 부작용 없음")
    void pay_whenOrderBelongsToAnotherUser_throwsOrderForbiddenBeforeClaimWithoutSideEffects() {
        Order order = pendingOrder(OTHER_USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertBusinessException(OrderForbiddenException.class, "ORDER_FORBIDDEN", () -> paymentService.pay(command()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
        verifyNoPaymentSideEffects();
    }

    @Test
    @DisplayName("TC-PAY-SVC-005: PENDING_PAYMENT가 아닌 주문이면 ORDER_CANNOT_PAY, claim 전 중단, 부작용 없음")
    void pay_whenOrderStatusIsNotPendingPayment_throwsOrderCannotPayBeforeClaimWithoutSideEffects() {
        for (OrderStatus status : List.of(
                OrderStatus.PAID,
                OrderStatus.CANCELLED,
                OrderStatus.EXPIRED,
                OrderStatus.PAYMENT_FAILED)) {
            Order order = orderWithStatus(status);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            assertBusinessException(OrderCannotPayException.class, "ORDER_CANNOT_PAY", () -> paymentService.pay(command()));

            assertThat(order.getStatus()).isEqualTo(status);
            verify(idempotencyRedisRepository, never()).claim(any(), any(), any(), any());
            verifyNoPaymentSideEffects();

            org.mockito.Mockito.reset(
                    orderRepository,
                    paymentRepository,
                    paymentTransactionRepository,
                    paymentGateway,
                    eventPublisher,
                    idempotencyRedisRepository);
            setUp();
        }
    }

    @Test
    @DisplayName("TC-PAY-SVC-006: PG 실패 시 Payment FAILED 영속, 주문 PENDING 유지, PaymentFailedException 재전파, claim release")
    void pay_whenGatewayFails_marksPaymentFailedKeepsOrderPendingAndReleasesClaim() {
        Order order = spy(pendingOrder(USER_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        PaymentFailedException gatewayFailure = new PaymentFailedException();
        when(paymentGateway.approve(any(PaymentApprovalCommand.class))).thenThrow(gatewayFailure);

        assertThatThrownBy(() -> paymentService.pay(command("idem-pay-pg-fail")))
                .isSameAs(gatewayFailure);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getAllValues().get(paymentCaptor.getAllValues().size() - 1);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedPayment.getFailedAt()).isEqualTo(NOW);
        assertThat(savedPayment.getApprovedAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(order, never()).markPaid();
        verify(paymentTransactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());
        verify(idempotencyRedisRepository).release(USER_ID, "idem-pay-pg-fail");
    }

    @Test
    @DisplayName("TC-PAY-SVC-012: PG 응답이 approved=false이면 Payment FAILED 영속, 주문 PENDING 유지, PaymentFailedException 재전파, claim release")
    void pay_whenGatewayReturnsNotApproved_marksPaymentFailedKeepsOrderPendingAndReleasesClaim() {
        Order order = spy(pendingOrder(USER_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentGateway.approve(any(PaymentApprovalCommand.class))).thenReturn(new PaymentApproval(
                false,
                EXTERNAL_TRANSACTION_ID,
                NOW,
                REQUEST_PAYLOAD,
                RESPONSE_PAYLOAD));

        assertThatThrownBy(() -> paymentService.pay(command("idem-pay-not-approved")))
                .isInstanceOf(PaymentFailedException.class);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getAllValues().get(paymentCaptor.getAllValues().size() - 1);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedPayment.getFailedAt()).isEqualTo(NOW);
        assertThat(savedPayment.getApprovedAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verify(order, never()).markPaid();
        verify(paymentTransactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());
        verify(idempotencyRedisRepository).release(USER_ID, "idem-pay-not-approved");
    }

    @Test
    @DisplayName("TC-PAY-SVC-007: 멱등 COMPLETED 재요청은 이전 PaymentResult 반환, PG 재호출 없음")
    void pay_whenIdempotencyClaimCompleted_returnsStoredResultWithoutReprocessing() {
        Order order = spy(pendingOrder(USER_ID));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.COMPLETED, COMPLETED_PAYMENT_PAYLOAD));

        PaymentResult result = paymentService.pay(command("idem-pay-completed"));

        assertThat(result.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(result.paymentNo()).isEqualTo(PAYMENT_NO);
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID.name());
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED.name());
        verify(paymentRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentGateway, never()).approve(any());
        verify(order, never()).markPaid();
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());
        verify(idempotencyRedisRepository, never()).release(any(), any());
    }

    @Test
    @DisplayName("TC-PAY-SVC-008: 멱등 MISMATCH는 IDEMPOTENCY_KEY_REUSED 예외, 결제 부작용 없음")
    void pay_whenIdempotencyClaimMismatched_throwsIdempotencyKeyReusedWithoutPaymentSideEffects() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.MISMATCH, null));

        assertBusinessException(
                IdempotencyKeyReusedException.class,
                "IDEMPOTENCY_KEY_REUSED",
                () -> paymentService.pay(command("idem-pay-reused")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoPaymentSideEffects();
    }

    @Test
    @DisplayName("TC-PAY-SVC-009: 멱등 IN_PROGRESS는 진행 중 예외, 결제 부작용 없음")
    void pay_whenIdempotencyClaimInProgress_throwsReservationInProgressWithoutPaymentSideEffects() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(idempotencyRedisRepository.claim(any(), any(), any(), any()))
                .thenReturn(new ClaimResult(ClaimStatus.IN_PROGRESS, null));

        assertBusinessException(
                ReservationInProgressException.class,
                "RESERVATION_IN_PROGRESS",
                () -> paymentService.pay(command("idem-pay-in-progress")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        verifyNoPaymentSideEffects();
    }

    @Test
    @DisplayName("TC-PAY-SVC-010: afterCommit에서는 이벤트 발행 후 멱등 complete를 수행하고 커밋 전에는 발행하지 않음")
    void pay_whenTransactionCommits_publishesEventThenCompletesIdempotencyAfterCommitOnly() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        TransactionSynchronizationManager.initSynchronization();

        paymentService.pay(command());

        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());

        triggerAfterCommit();

        InOrder afterCommitOrder = inOrder(eventPublisher, idempotencyRedisRepository);
        afterCommitOrder.verify(eventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
        afterCommitOrder.verify(idempotencyRedisRepository).complete(eq(USER_ID), eq(IDEMPOTENCY_KEY), any(), any());
    }

    @Test
    @DisplayName("TC-PAY-SVC-011: 롤백 시 afterCompletion에서 claim release, 이벤트/complete 미호출")
    void pay_whenTransactionRollsBack_releasesClaimWithoutPublishingOrCompleting() {
        Order order = pendingOrder(USER_ID);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenThrow(new RuntimeException("database unavailable"));
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> paymentService.pay(command("idem-pay-rollback")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("database unavailable");

        triggerAfterCompletionRollback();

        verify(idempotencyRedisRepository).release(USER_ID, "idem-pay-rollback");
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());
        assertThat(order.getStatus()).isNotEqualTo(OrderStatus.PAID);
    }

    private void assertRequestTransaction(PaymentTransaction transaction) {
        assertThat(transaction.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.REQUEST);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getRequestPayload()).isEqualTo(REQUEST_PAYLOAD);
    }

    private void assertApproveTransaction(PaymentTransaction transaction) {
        assertThat(transaction.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.APPROVE);
        assertThat(transaction.getTransactionStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getExternalTransactionId()).isEqualTo(EXTERNAL_TRANSACTION_ID);
        assertThat(transaction.getResponsePayload()).isEqualTo(RESPONSE_PAYLOAD);
    }

    private void verifyNoPaymentSideEffects() {
        verify(paymentRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentGateway, never()).approve(any());
        verify(eventPublisher, never()).publishPaymentCompleted(any());
        verify(idempotencyRedisRepository, never()).complete(any(), any(), any(), any());
        verify(idempotencyRedisRepository, never()).release(any(), any());
    }

    private PaymentCommand command() {
        return command(AMOUNT, IDEMPOTENCY_KEY);
    }

    private PaymentCommand command(String idempotencyKey) {
        return command(AMOUNT, idempotencyKey);
    }

    private PaymentCommand command(BigDecimal amount, String idempotencyKey) {
        return new PaymentCommand(USER_ID, ORDER_ID, PAYMENT_KEY, amount, idempotencyKey);
    }

    private PaymentApproval paymentApproval() {
        return new PaymentApproval(
                true,
                EXTERNAL_TRANSACTION_ID,
                NOW,
                REQUEST_PAYLOAD,
                RESPONSE_PAYLOAD);
    }

    private Order pendingOrder(Long userId) {
        Order order = Order.create(
                userId,
                EVENT_ID,
                List.of(OrderItem.create(
                        EVENT_ITEM_ID,
                        OPTION_STOCK_ID,
                        "색상",
                        "Black",
                        AMOUNT.divide(BigDecimal.valueOf(QUANTITY)),
                        QUANTITY)),
                NOW.minusMinutes(10),
                PAYMENT_TTL);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "totalAmount", AMOUNT);
        return order;
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = pendingOrder(USER_ID);
        if (status == OrderStatus.PAID) {
            order.markPaid();
            return order;
        }
        if (status == OrderStatus.CANCELLED) {
            order.cancel(NOW.minusMinutes(1));
            return order;
        }
        if (status == OrderStatus.EXPIRED) {
            order.expire(NOW.minusMinutes(1));
            return order;
        }
        ReflectionTestUtils.setField(order, "status", status);
        return order;
    }

    private void triggerAfterCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCommit();
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCompletionRollback() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void assertBusinessException(
            Class<? extends BusinessException> expectedType,
            String expectedCode,
            ThrowingCallable callable
    ) {
        assertThatThrownBy(callable::call)
                .isInstanceOf(expectedType)
                .extracting("code")
                .isEqualTo(expectedCode);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call();
    }
}
