package com.mist.commerce.domain.user.repository;

import com.mist.commerce.domain.user.entity.RefreshTokenSession;
import com.mist.commerce.domain.user.entity.SessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenSessionRepository extends JpaRepository<RefreshTokenSession, Long> {

    Optional<RefreshTokenSession> findBySessionId(String sessionId);

    List<RefreshTokenSession> findAllByUserIdAndStatus(Long userId, SessionStatus status);

    @Modifying
    @Transactional
    void deleteBySessionId(String sessionId);
}
