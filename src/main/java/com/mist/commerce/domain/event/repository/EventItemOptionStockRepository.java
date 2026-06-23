package com.mist.commerce.domain.event.repository;

import com.mist.commerce.domain.event.entity.EventItemOptionStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventItemOptionStockRepository extends JpaRepository<EventItemOptionStock, Long> {
}
