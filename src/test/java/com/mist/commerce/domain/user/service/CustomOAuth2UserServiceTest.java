package com.mist.commerce.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mist.commerce.domain.user.entity.OAuthAccount;
import com.mist.commerce.domain.user.entity.OAuthProvider;
import com.mist.commerce.domain.user.entity.User;
import com.mist.commerce.domain.user.entity.UserStatus;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.domain.user.repository.OAuthAccountRepository;
import com.mist.commerce.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Mock
    UserRepository userRepository;

    @Mock
    OAuthAccountRepository oAuthAccountRepository;

    @InjectMocks
    CustomOAuth2UserService service;

    // 통합 테스트로 이관 - CustomOAuth2UserServiceIntegrationTest

    @Test
    void loadUser_기존_OAuthAccount_존재하면_isNewUser_false_반환() {
        User existingUser = user("a@test.com", "홍길동", UserType.USER, 10L);
        OAuthAccount account = OAuthAccount.of(existingUser, OAuthProvider.GOOGLE, "sub-123", "a@test.com");
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.of(account));

        OAuth2User result = service.loadUser(request);

        assertThat((Boolean) result.getAttribute("isNewUser")).isFalse();
        assertThat((Long) result.getAttribute("userId")).isEqualTo(10L);
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_기존_OAuthAccount_없고_email도_없으면_신규_User_OAuthAccount_생성() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("a@test.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });

        OAuth2User result = service.loadUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OAuthAccount> accountCaptor = ArgumentCaptor.forClass(OAuthAccount.class);
        verify(userRepository).save(userCaptor.capture());
        verify(oAuthAccountRepository).save(accountCaptor.capture());
        User savedUser = userCaptor.getValue();
        OAuthAccount savedAccount = accountCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("a@test.com");
        assertThat(savedUser.getName()).isEqualTo("홍길동");
        assertThat(savedUser.getUserType()).isEqualTo(UserType.USER);
        assertThat(savedAccount.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(savedAccount.getProviderUserId()).isEqualTo("sub-123");
        assertThat(savedAccount.getProviderEmail()).isEqualTo("a@test.com");
        assertThat((Boolean) result.getAttribute("isNewUser")).isTrue();
    }

    @Test
    void loadUser_신규_가입_시_User와_OAuthAccount_저장_순서_보장() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("a@test.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.loadUser(request);

        InOrder inOrder = inOrder(userRepository, oAuthAccountRepository);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(oAuthAccountRepository).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_email_대소문자_그대로_보존() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "A@Test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("A@Test.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.loadUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("A@Test.com");
    }

    @Test
    void loadUser_반환_OAuth2User_principalName이_sub인지() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("a@test.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        OAuth2User result = service.loadUser(request);

        assertThat(result.getName()).isEqualTo("sub-123");
    }

    @Test
    void loadUser_OAuthAccount_없고_email로_USER_타입_계정_있으면_USER_EMAIL_DUPLICATED를_cause로_감싼_OAuth2AuthenticationException() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("a@test.com"))
                .willReturn(Optional.of(user("a@test.com", "홍길동", UserType.USER, 10L)));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasCauseInstanceOf(UserEmailDuplicatedException.class)
                .hasMessageContaining("a@test.com");
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_OAuthAccount_없고_email로_COMPANY_타입_계정_있으면_OAUTH_ACCOUNT_ALREADY_LINKED_TO_BUSINESS를_cause로_감싼_OAuth2AuthenticationException() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", "a@test.com", "홍길동");
        given(oAuthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "sub-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("a@test.com"))
                .willReturn(Optional.of(user("a@test.com", "홍길동", UserType.COMPANY, 10L)));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasCauseInstanceOf(OAuthAccountAlreadyLinkedToBusinessException.class);
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_email_누락_미동의_이면_OAuth2AuthenticationException_또는_도메인_예외() {
        OAuth2UserRequest request = givenGoogleRequest("sub-123", null, "홍길동");

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(Exception.class);
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_sub_누락이면_예외() {
        OAuth2UserRequest request = givenGoogleRequest(null, "a@test.com", "홍길동");

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(Exception.class);
        verify(userRepository, never()).save(any(User.class));
        verify(oAuthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loadUser_delegate에서_OAuth2AuthenticationException_throw하면_그대로_전파() {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(new OAuth2Error("invalid"));
        OAuth2UserRequest request = googleRequest();
        given(delegate.loadUser(request)).willThrow(exception);

        assertThatThrownBy(() -> service.loadUser(request)).isSameAs(exception);
        verifyNoInteractions(userRepository, oAuthAccountRepository);
    }

    private OAuth2UserRequest givenGoogleRequest(String sub, String email, String name) {
        OAuth2UserRequest request = googleRequest();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", sub);
        attrs.put("email", email);
        attrs.put("name", name);
        String nameAttributeKey = sub == null ? "email" : "sub";
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, nameAttributeKey);
        given(delegate.loadUser(request)).willReturn(principal);
        return request;
    }

    private OAuth2UserRequest googleRequest() {
        return new OAuth2UserRequest(clientRegistration(), accessToken());
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("client")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scope("email", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }

    private OAuth2AccessToken accessToken() {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                Instant.parse("2026-05-07T10:00:00Z"),
                Instant.parse("2026-05-07T11:00:00Z")
        );
    }

    private User user(String email, String name, UserType userType, Long id) {
        User user = User.createPersonal(email, name);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "userType", userType);
        ReflectionTestUtils.setField(user, "status", UserStatus.ACTIVE);
        return user;
    }
}
