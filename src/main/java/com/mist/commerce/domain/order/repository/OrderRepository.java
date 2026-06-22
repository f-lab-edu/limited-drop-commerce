package com.mist.commerce.domain.order.repository;

import com.mist.commerce.domain.order.entity.Order;
import com.mist.commerce.domain.order.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(Long id);

    boolean existsByUserIdAndEventIdAndStatus(Long userId, Long eventId, OrderStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = com.mist.commerce.domain.order.entity.OrderStatus.EXPIRED, o.expiredAt = :now "
            + "WHERE o.id = :id AND o.status = com.mist.commerce.domain.order.entity.OrderStatus.PENDING_PAYMENT")
    int expireIfPending(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("SELECT o.id FROM Order o "
            + "WHERE o.status = com.mist.commerce.domain.order.entity.OrderStatus.PENDING_PAYMENT AND o.expiresAt < :now "
            + "ORDER BY o.expiresAt ASC")
    List<Long> findExpiredPendingPaymentIds(@Param("now") LocalDateTime now, Pageable pageable);

}
