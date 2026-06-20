package com.mist.commerce.domain.reservation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class InventoryReservationRepositoryTest extends MySqlContainerTestSupport {

    private static final Long ORDER_ID = 1000L;
    private static final LocalDateTime RESERVED_AT = LocalDateTime.of(2026, 6, 17, 12, 0);
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(10);

    @Autowired
    private InventoryReservationRepository inventoryReservationRepository;

    @Test
    @DisplayName("TC-ORR-005: orderId로 선점 목록을 조회한다")
    void findByOrderId_whenReservationsExist_returnsReservationsForOrder() {
        InventoryReservation first = reservation(ORDER_ID, 1L, 11L, 1);
        InventoryReservation second = reservation(ORDER_ID, 2L, 22L, 2);
        inventoryReservationRepository.saveAllAndFlush(List.of(first, second, reservation(2000L, 3L, 33L, 1)));

        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(ORDER_ID);

        assertThat(reservations).hasSize(2);
        assertThat(reservations).extracting(InventoryReservation::getOrderId)
                .containsOnly(ORDER_ID);
        assertThat(reservations).extracting(InventoryReservation::getEventItemId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("TC-ORR-006: orderId에 해당하는 선점이 없으면 빈 목록을 반환한다")
    void findByOrderId_whenReservationsDoNotExist_returnsEmptyList() {
        inventoryReservationRepository.saveAllAndFlush(List.of(
                reservation(ORDER_ID, 1L, 11L, 1),
                reservation(ORDER_ID, 2L, 22L, 2)));

        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderId(9999L);

        assertThat(reservations).isEmpty();
    }

    private InventoryReservation reservation(
            Long orderId,
            Long eventItemId,
            Long eventItemOptionId,
            int reservedQuantity
    ) {
        return InventoryReservation.create(
                orderId,
                eventItemId,
                eventItemOptionId,
                reservedQuantity,
                RESERVED_AT,
                RESERVATION_TTL);
    }
}
