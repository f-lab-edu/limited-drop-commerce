package com.mist.commerce.domain.reservation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.order.repository.OrderRepository;
import com.mist.commerce.domain.reservation.service.ExpiryRecoveryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ExpiredOrderSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExpiryRecoveryService expiryRecoveryService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private ExpiredOrderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExpiredOrderScheduler(orderRepository, expiryRecoveryService, CLOCK);
    }

    @Test
    @DisplayName("TC-EXPIRED-SCHED-001: 조회된 만료 PENDING 주문만 복구 위임한다")
    void recoverExpiredOrders_recoversOnlyExpiredPendingOrderIdsReturnedByRepository() {
        when(orderRepository.findExpiredPendingPaymentIds(eq(NOW), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(1L, 2L));

        scheduler.recoverExpiredOrders();

        verify(expiryRecoveryService).recover(1L);
        verify(expiryRecoveryService).recover(2L);
        verify(expiryRecoveryService, never()).recover(99L);
    }

    @Test
    @DisplayName("TC-EXPIRED-SCHED-002: 개별 주문 복구 예외를 격리하고 다음 주문을 계속 처리한다")
    void recoverExpiredOrders_continuesWhenOneRecoveryThrows() {
        when(orderRepository.findExpiredPendingPaymentIds(eq(NOW), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException()).when(expiryRecoveryService).recover(2L);

        assertThatCode(scheduler::recoverExpiredOrders)
                .doesNotThrowAnyException();

        verify(expiryRecoveryService).recover(1L);
        verify(expiryRecoveryService).recover(2L);
        verify(expiryRecoveryService).recover(3L);
    }

    @Test
    @DisplayName("TC-EXPIRED-SCHED-003: BATCH_SIZE 100으로 만료 주문을 조회한다")
    void recoverExpiredOrders_queriesExpiredOrdersWithBatchSize() {
        scheduler.recoverExpiredOrders();

        verify(orderRepository).findExpiredPendingPaymentIds(eq(NOW), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }
}
