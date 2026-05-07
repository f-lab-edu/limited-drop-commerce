package com.mist.commerce.domain.user.repository;

import com.mist.commerce.domain.user.entity.LoginHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
}
