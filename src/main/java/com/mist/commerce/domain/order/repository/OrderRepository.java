package com.mist.commerce.domain.order.repository;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByUserIdAndEventIdAndStatus(Long userId, Long eventId, OrderStatus status);

}
