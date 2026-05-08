package com.mist.commerce.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.user.dto.AuthTokenResponse;
import com.mist.commerce.domain.user.entity.LoginHistory;
import com.mist.commerce.domain.user.entity.LoginType;
import com.mist.commerce.domain.user.entity.RefreshTokenSession;
import com.mist.commerce.domain.user.entity.SessionStatus;
import com.mist.commerce.domain.user.repository.LoginHistoryRepository;
import com.mist.commerce.domain.user.repository.RefreshTokenSessionRepository;
import com.mist.commerce.global.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    TokenService tokenService;

    @Mock
    RefreshTokenSessionRepository refreshTokenSessionRepository;

    @Mock
    LoginHistoryRepository loginHistoryRepository;

    @Mock
    JwtProperties jwtProperties;

    @Mock
    Clock clock;

    private AuthService authService;

    // 통합 테스트로 이관 - AuthServiceIntegrationTest

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                tokenService,
                refreshTokenSessionRepository,
                loginHistoryRepository,
                jwtProperties,
                clock
        );
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-05-07T10:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient().when(jwtProperties.refreshTokenExpiry()).thenReturn(1_209_600_000L);
    }

    @Test
    void login_AccessToken_생성_AuthTokenResponse에_포함() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        AuthTokenResponse response = authService.login(oAuth2User, request);

        assertThat(response.accessToken()).isEqualTo("access-stub");
        verify(tokenService).generateAccessToken(1L, "USER", "ACTIVE");
    }

    @Test
    void login_RefreshToken_생성_AuthTokenResponse에_포함() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        AuthTokenResponse response = authService.login(oAuth2User, request);

        assertThat(response.refreshToken()).isEqualTo("refresh-stub");
    }

    @Test
    void login_isNewUser_플래그_그대로_전달() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        AuthTokenResponse response = authService.login(oAuth2User, request);

        assertThat(response.isNewUser()).isTrue();
    }

    @Test
    void login_RefreshTokenSession_DB에_저장() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        ArgumentCaptor<RefreshTokenSession> captor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(refreshTokenSessionRepository).save(captor.capture());
        RefreshTokenSession session = captor.getValue();
        assertThat(session.getUserId()).isEqualTo(1L);
        assertThat(session.getSessionId()).isEqualTo("uuid-1");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(session.getUserAgent()).isEqualTo("Mozilla/5.0 ...");
        assertThat(session.getExpiredAt()).isEqualTo(LocalDateTime.parse("2026-05-21T10:00:00"));
    }

    @Test
    void login_LoginHistory_성공_이력_저장() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        ArgumentCaptor<LoginHistory> captor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(loginHistoryRepository).save(captor.capture());
        LoginHistory history = captor.getValue();
        assertThat(history.getMemberId()).isEqualTo(1L);
        assertThat(history.getLoginType()).isEqualTo(LoginType.GOOGLE);
        assertThat(history.getSuccessYn()).isEqualTo("Y");
        assertThat(history.getFailureReason()).isNull();
        assertThat(history.getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(history.getUserAgent()).isEqualTo("Mozilla/5.0 ...");
    }

    @Test
    void login_X_Forwarded_For_헤더_있으면_우선_사용() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest("198.51.100.7, 10.0.0.1", "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        ArgumentCaptor<RefreshTokenSession> sessionCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        ArgumentCaptor<LoginHistory> historyCaptor = ArgumentCaptor.forClass(LoginHistory.class);
        verify(refreshTokenSessionRepository).save(sessionCaptor.capture());
        verify(loginHistoryRepository).save(historyCaptor.capture());
        assertThat(sessionCaptor.getValue().getIpAddress()).isEqualTo("198.51.100.7");
        assertThat(historyCaptor.getValue().getIpAddress()).isEqualTo("198.51.100.7");
    }

    @Test
    void login_X_Forwarded_For_없으면_remoteAddr_사용() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        ArgumentCaptor<RefreshTokenSession> sessionCaptor = ArgumentCaptor.forClass(RefreshTokenSession.class);
        verify(refreshTokenSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    void login_기존_사용자_isNewUser_false_그대로_반환() {
        OAuth2User oAuth2User = givenOAuth2User(1L, false);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        AuthTokenResponse response = authService.login(oAuth2User, request);

        assertThat(response.isNewUser()).isFalse();
    }

    @Test
    void login_기존_사용자도_새로운_RefreshTokenSession_저장() {
        OAuth2User oAuth2User = givenOAuth2User(1L, false);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        verify(refreshTokenSessionRepository).save(any(RefreshTokenSession.class));
    }

    @Test
    void login_TokenService_Redis저장_DB저장_LoginHistory_순서_보장() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        givenSuccessfulTokens(1L);

        authService.login(oAuth2User, request);

        InOrder inOrder = inOrder(tokenService, refreshTokenSessionRepository, loginHistoryRepository);
        inOrder.verify(tokenService).generateAccessToken(1L, "USER", "ACTIVE");
        inOrder.verify(tokenService).generateRefreshToken(1L);
        inOrder.verify(refreshTokenSessionRepository).save(any(RefreshTokenSession.class));
        inOrder.verify(loginHistoryRepository).save(any(LoginHistory.class));
    }

    @Test
    void login_RefreshTokenSession_저장_실패시_LoginHistory_저장되지_않음() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate session");
        givenSuccessfulTokens(1L);
        willThrow(exception).given(refreshTokenSessionRepository).save(any(RefreshTokenSession.class));

        assertThatThrownBy(() -> authService.login(oAuth2User, request))
                .isSameAs(exception);
        verify(loginHistoryRepository, never()).save(any(LoginHistory.class));
    }

    @Test
    void login_oAuth2User_null이면_예외() {
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");

        assertThatThrownBy(() -> authService.login(null, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_request_null이면_예외() {
        OAuth2User oAuth2User = givenOAuth2User(1L, true);

        assertThatThrownBy(() -> authService.login(oAuth2User, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_userId_attribute_null이면_예외() {
        OAuth2User oAuth2User = givenOAuth2User(null, true);
        MockHttpServletRequest request = givenRequest(null, "203.0.113.10", "Mozilla/5.0 ...");

        assertThatThrownBy(() -> authService.login(oAuth2User, request))
                .isInstanceOf(IllegalStateException.class);
        verify(refreshTokenSessionRepository, never()).save(any(RefreshTokenSession.class));
        verify(loginHistoryRepository, never()).save(any(LoginHistory.class));
    }

    private void givenSuccessfulTokens(Long userId) {
        given(tokenService.generateAccessToken(userId, "USER", "ACTIVE")).willReturn("access-stub");
        given(tokenService.generateRefreshToken(userId)).willReturn(new RefreshTokenPair("uuid-1", "refresh-stub"));
    }

    private OAuth2User givenOAuth2User(Long userId, boolean isNewUser) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        attrs.put("userType", "USER");
        attrs.put("status", "ACTIVE");
        attrs.put("isNewUser", isNewUser);
        attrs.put("sub", "sub-123");
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), attrs, "sub");
    }

    private MockHttpServletRequest givenRequest(String xff, String remoteAddr, String ua) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        if (remoteAddr != null) {
            req.setRemoteAddr(remoteAddr);
        }
        if (ua != null) {
            req.addHeader("User-Agent", ua);
        }
        return req;
    }
}
