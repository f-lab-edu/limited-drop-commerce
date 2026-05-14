package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RefreshTokenSessionTest {

    @Test
    @DisplayName("만료 시간이 현재보다 이전이면 만료 상태다")
    void isExpiredWithPastExpiredAt() {
        RefreshTokenSession session = session(SessionStatus.ACTIVE, LocalDateTime.now().minusSeconds(1));

        assertThat(session.isExpired()).isTrue();
    }

    @Test
    @DisplayName("만료 시간이 현재와 같거나 이후이면 만료 상태가 아니다")
    void isExpiredBoundary() {
        RefreshTokenSession session = session(SessionStatus.ACTIVE, LocalDateTime.now().plusSeconds(1));

        assertThat(session.isExpired()).isFalse();
    }

    @Test
    @DisplayName("세션을 철회하면 상태가 REVOKED로 변경된다")
    void revoke() {
        RefreshTokenSession session = session(SessionStatus.ACTIVE, LocalDateTime.now().plusDays(1));

        session.revoke();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.REVOKED);
    }

    @Test
    @DisplayName("이미 철회된 세션은 다시 철회할 수 없다")
    void cannotRevokeTwice() {
        RefreshTokenSession session = session(SessionStatus.REVOKED, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(session::revoke)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시간이 정확히 현재와 같으면 만료 상태다")
    void isExpiredWhenExactlyNow() {
        RefreshTokenSession session = session(SessionStatus.ACTIVE, LocalDateTime.now());

        assertThat(session.isExpired()).isTrue();
    }

    private RefreshTokenSession session(SessionStatus status, LocalDateTime expiredAt) {
        RefreshTokenSession session = new RefreshTokenSession();
        ReflectionTestUtils.setField(session, "status", status);
        ReflectionTestUtils.setField(session, "expiredAt", expiredAt);
        return session;
    }
}
