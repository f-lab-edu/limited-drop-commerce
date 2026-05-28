package com.mist.commerce.domain.event.repository;

import com.mist.commerce.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
