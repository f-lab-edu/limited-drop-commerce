package com.mist.commerce.domain.reservation.repository;

import com.mist.commerce.domain.reservation.entity.InventoryReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOrderId(Long orderId);

}
