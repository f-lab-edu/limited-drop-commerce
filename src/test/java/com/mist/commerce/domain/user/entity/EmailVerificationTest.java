package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailVerificationTest {

    @Test
    @DisplayName("만료 시간이 현재보다 이전이면 만료 상태다")
    void isExpiredWithPastExpiredAt() {
        EmailVerification verification = verification(LocalDateTime.now().minusSeconds(1), null);

        assertThat(verification.isExpired()).isTrue();
    }

    @Test
    @DisplayName("만료 시간이 현재와 같거나 이후이면 만료 상태가 아니다")
    void isExpiredBoundary() {
        EmailVerification verification = verification(LocalDateTime.now().plusSeconds(1), null);

        assertThat(verification.isExpired()).isFalse();
    }

    @Test
    @DisplayName("이메일 인증을 완료하면 verifiedAt이 기록된다")
    void verify() {
        EmailVerification verification = verification(LocalDateTime.now().plusMinutes(10), null);

        verification.verify();

        assertThat(verification.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 인증된 토큰은 다시 인증할 수 없다")
    void cannotVerifyTwice() {
        EmailVerification verification = verification(LocalDateTime.now().plusMinutes(10), LocalDateTime.now());

        assertThatThrownBy(verification::verify)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시간이 정확히 현재와 같으면 만료 상태다")
    void isExpiredWhenExactlyNow() {
        EmailVerification verification = verification(LocalDateTime.now(), null);

        assertThat(verification.isExpired()).isTrue();
    }

    private EmailVerification verification(LocalDateTime expiredAt, LocalDateTime verifiedAt) {
        EmailVerification verification = new EmailVerification();
        ReflectionTestUtils.setField(verification, "expiredAt", expiredAt);
        ReflectionTestUtils.setField(verification, "verifiedAt", verifiedAt);
        return verification;
    }
}
