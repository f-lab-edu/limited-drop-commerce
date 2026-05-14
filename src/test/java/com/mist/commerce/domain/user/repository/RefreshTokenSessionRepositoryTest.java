package com.mist.commerce.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import com.mist.commerce.domain.user.entity.RefreshTokenSession;
import com.mist.commerce.domain.user.entity.SessionStatus;
import com.mist.commerce.global.config.JpaAuditingConfig;
import com.mist.commerce.support.MySqlContainerTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class RefreshTokenSessionRepositoryTest extends MySqlContainerTestSupport {

    @Autowired
    private RefreshTokenSessionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findBySessionId_존재하면_세션_반환() {
        // given
        entityManager.persistAndFlush(session(1L, "uuid-abc", SessionStatus.ACTIVE));

        // when
        Optional<RefreshTokenSession> found = repository.findBySessionId("uuid-abc");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo("uuid-abc");
    }

    @Test
    void findBySessionId_없으면_empty() {
        // given

        // when
        Optional<RefreshTokenSession> found = repository.findBySessionId("uuid-xyz");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void findAllByUserIdAndStatus_ACTIVE_세션만_반환() {
        // given
        entityManager.persist(session(1L, "uuid-1", SessionStatus.ACTIVE));
        entityManager.persist(session(1L, "uuid-2", SessionStatus.ACTIVE));
        entityManager.persist(session(1L, "uuid-3", SessionStatus.REVOKED));
        entityManager.persist(session(1L, "uuid-4", SessionStatus.EXPIRED));
        entityManager.flush();

        // when
        List<RefreshTokenSession> result = repository.findAllByUserIdAndStatus(1L, SessionStatus.ACTIVE);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(session -> session.getStatus() == SessionStatus.ACTIVE);
    }

    @Test
    void findAllByUserIdAndStatus_다른_userId_제외() {
        // given
        entityManager.persist(session(1L, "uuid-1", SessionStatus.ACTIVE));
        entityManager.persist(session(1L, "uuid-2", SessionStatus.ACTIVE));
        entityManager.persist(session(2L, "uuid-3", SessionStatus.ACTIVE));
        entityManager.flush();

        // when
        List<RefreshTokenSession> result = repository.findAllByUserIdAndStatus(1L, SessionStatus.ACTIVE);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(session -> session.getUserId().equals(1L));
    }

    @Test
    void findAllByUserIdAndStatus_매칭_없으면_빈_리스트() {
        // given

        // when
        List<RefreshTokenSession> result = repository.findAllByUserIdAndStatus(1L, SessionStatus.ACTIVE);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void deleteBySessionId_해당_세션_삭제() {
        // given
        entityManager.persistAndFlush(session(1L, "uuid-abc", SessionStatus.ACTIVE));

        // when
        repository.deleteBySessionId("uuid-abc");
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(repository.findBySessionId("uuid-abc")).isEmpty();
    }

    @Test
    void deleteBySessionId_삭제_후_flush_clear_반영() {
        // given
        entityManager.persistAndFlush(session(1L, "uuid-abc", SessionStatus.ACTIVE));

        // when
        repository.deleteBySessionId("uuid-abc");
        repository.flush();
        entityManager.clear();

        // then
        assertThat(repository.findBySessionId("uuid-abc")).isEmpty();
    }

    @Test
    void deleteBySessionId_존재하지_않으면_예외_없음() {
        // given

        // when & then
        assertThatCode(() -> repository.deleteBySessionId("uuid-none"))
                .doesNotThrowAnyException();
    }

    @Test
    void sessionId_unique_constraint_중복_저장_예외() {
        // given
        repository.saveAndFlush(session(1L, "uuid-abc", SessionStatus.ACTIVE));
        RefreshTokenSession duplicate = session(2L, "uuid-abc", SessionStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionId_null_저장_시_예외() {
        // given
        RefreshTokenSession session = session(1L, null, SessionStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> repository.saveAndFlush(session))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_status_enum_STRING_저장_확인() {
        // given
        repository.saveAndFlush(session(1L, "uuid-abc", SessionStatus.ACTIVE));
        entityManager.clear();

        // when
        Object status = entityManager.getEntityManager()
                .createNativeQuery("select status from refresh_token_sessions where session_id = ?")
                .setParameter(1, "uuid-abc")
                .getSingleResult();

        // then
        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void save_expiredAt_저장_및_isExpired_연동() {
        // given
        RefreshTokenSession session = session(1L, "uuid-abc", SessionStatus.ACTIVE);

        // when
        repository.saveAndFlush(session);
        entityManager.clear();
        RefreshTokenSession found = repository.findBySessionId("uuid-abc").get();

        // then
        assertThat(found.getExpiredAt()).isNotNull();
        assertThat(found.isExpired()).isFalse();
    }

    private RefreshTokenSession session(Long userId, String sessionId, SessionStatus status) {
        return RefreshTokenSession.of(userId, sessionId, status, LocalDateTime.now().plusDays(14));
    }
}
