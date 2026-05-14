package com.mist.commerce.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthAccountTest {

    @Test
    @DisplayName("OAuth 계정을 생성한다")
    void of() {
        User user = User.createPersonal("user@example.com", "홍길동");

        OAuthAccount oauthAccount = OAuthAccount.of(user, OAuthProvider.GOOGLE, "google-sub", "user@gmail.com");

        assertThat(oauthAccount.getUser()).isSameAs(user);
        assertThat(oauthAccount.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(oauthAccount.getProviderUserId()).isEqualTo("google-sub");
        assertThat(oauthAccount.getProviderEmail()).isEqualTo("user@gmail.com");
    }

    @Test
    @DisplayName("OAuth 제공자 이메일은 null을 허용한다")
    void providerEmailCanBeNull() {
        User user = User.createPersonal("user@example.com", "홍길동");

        OAuthAccount oauthAccount = OAuthAccount.of(user, OAuthProvider.GOOGLE, "google-sub", null);

        assertThat(oauthAccount.getProviderEmail()).isNull();
    }
}
