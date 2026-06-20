package com.mist.commerce.domain.event.repository;

import com.mist.commerce.domain.event.entity.Event;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.optionStocks"})
    Optional<Event> findById(Long id);

}
