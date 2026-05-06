package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginHistoryTest {

    @Test
    @DisplayName("성공 로그인 이력을 생성한다")
    void success() {
        LoginHistory history = LoginHistory.success(1L, LoginType.GOOGLE, "127.0.0.1", "Mozilla/5.0");

        assertThat(history.getMemberId()).isEqualTo(1L);
        assertThat(history.getLoginType()).isEqualTo(LoginType.GOOGLE);
        assertThat(history.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(history.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(history.getSuccessYn()).isEqualTo("Y");
        assertThat(history.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("실패 로그인 이력을 생성한다")
    void failure() {
        LoginHistory history = LoginHistory.failure(
                1L,
                LoginType.GOOGLE,
                "127.0.0.1",
                "Mozilla/5.0",
                "USER_EMAIL_DUPLICATED"
        );

        assertThat(history.getMemberId()).isEqualTo(1L);
        assertThat(history.getLoginType()).isEqualTo(LoginType.GOOGLE);
        assertThat(history.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(history.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(history.getSuccessYn()).isEqualTo("N");
        assertThat(history.getFailureReason()).isEqualTo("USER_EMAIL_DUPLICATED");
    }

    @Test
    @DisplayName("이메일 로그인 타입으로 이력을 생성한다")
    void emailLoginType() {
        LoginHistory history = LoginHistory.success(1L, LoginType.EMAIL, "127.0.0.1", "Mozilla/5.0");

        assertThat(history.getLoginType()).isEqualTo(LoginType.EMAIL);
    }

    @Test
    @DisplayName("IP와 User-Agent 없이 로그인 이력을 생성한다")
    void ipAddressAndUserAgentCanBeNull() {
        LoginHistory history = LoginHistory.success(1L, LoginType.GOOGLE, null, null);

        assertThat(history.getIpAddress()).isNull();
        assertThat(history.getUserAgent()).isNull();
    }
}
