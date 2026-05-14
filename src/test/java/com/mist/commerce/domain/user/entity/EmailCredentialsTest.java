package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailCredentialsTest {

    @Test
    @DisplayName("이메일 인증 정보를 생성한다")
    void of() {
        EmailCredentials credentials = EmailCredentials.of("user@example.com", "encoded-password");

        assertThat(credentials.getEmail()).isEqualTo("user@example.com");
        assertThat(credentials.getPassword()).isEqualTo("encoded-password");
        assertThat(credentials.getVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("verifiedAt이 있으면 인증 완료 상태다")
    void isVerifiedTrue() {
        EmailCredentials credentials = new EmailCredentials();
        ReflectionTestUtils.setField(credentials, "verifiedAt", LocalDateTime.now());

        assertThat(credentials.isVerified()).isTrue();
    }

    @Test
    @DisplayName("verifiedAt이 없으면 미인증 상태다")
    void isVerifiedFalse() {
        EmailCredentials credentials = new EmailCredentials();

        assertThat(credentials.isVerified()).isFalse();
    }
}
