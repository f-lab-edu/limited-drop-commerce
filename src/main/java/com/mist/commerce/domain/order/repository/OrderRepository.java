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
    @Query("UPDATE Order o SET o.status = :to, o.expiredAt = :now WHERE o.id = :id AND o.status = :from")
    int expireIfPending(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Order o SET o.status = :to, o.cancelledAt = :now WHERE o.id = :id AND o.status = :from")
    int cancelIfPending(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("from") OrderStatus from,
            @Param("to") OrderStatus to);

    @Query("SELECT o.id FROM Order o WHERE o.status = :status AND o.expiresAt < :now ORDER BY o.expiresAt ASC")
    List<Long> findExpiredPendingPaymentIds(
            @Param("status") OrderStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

}
