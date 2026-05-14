package com.mist.commerce.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.user.dto.AuthTokenResponse;
import com.mist.commerce.domain.user.service.AuthService;
import com.mist.commerce.global.response.ApiResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    AuthService authService;

    @Mock
    Authentication authentication;

    ObjectMapper objectMapper;
    OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new OAuth2LoginSuccessHandler(
                authService,
                objectMapper,
                java.time.Clock.fixed(java.time.Instant.parse("2026-05-09T10:00:00Z"), java.time.ZoneOffset.UTC)
        );
    }

    @Test
    void onAuthenticationSuccess_정상_흐름이면_200_OK_application_json_응답() throws Exception {
        OAuth2User oAuth2User = givenOAuth2User();
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(authService.login(any(OAuth2User.class), any()))
                .willReturn(new AuthTokenResponse("at", "rt", true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void onAuthenticationSuccess_응답_바디_ApiResponse_봉투_구조() throws Exception {
        OAuth2User oAuth2User = givenOAuth2User();
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(authService.login(any(OAuth2User.class), any()))
                .willReturn(new AuthTokenResponse("at", "rt", true));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        ApiResponse<AuthTokenResponse> body = objectMapper.readValue(
                response.getContentAsString(),
                new TypeReference<>() {
                }
        );
        assertThat(body.success()).isTrue();
        assertThat(body.code()).isEqualTo("OK");
        assertThat(body.errors()).isNull();
        assertThat(body.data().accessToken()).isEqualTo("at");
        assertThat(body.data().refreshToken()).isEqualTo("rt");
        assertThat(body.data().isNewUser()).isTrue();
    }

    @Test
    void onAuthenticationSuccess_AuthService에_원본_request_그대로_전달() throws Exception {
        OAuth2User oAuth2User = givenOAuth2User();
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(authService.login(any(OAuth2User.class), any()))
                .willReturn(new AuthTokenResponse("at", "rt", true));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        request.addHeader("User-Agent", "Mozilla/5.0");

        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(), authentication);

        verify(authService).login(eq(oAuth2User), same(request));
    }

    @Test
    void onAuthenticationSuccess_principal이_OAuth2User_아니면_IllegalStateException() throws Exception {
        given(authentication.getPrincipal()).willReturn("anonymousUser");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication))
                .isInstanceOf(IllegalStateException.class);
        verify(authService, never()).login(any(), any());
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void onAuthenticationSuccess_principal이_null이면_IllegalStateException() {
        given(authentication.getPrincipal()).willReturn(null);

        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), new MockHttpServletResponse(), authentication))
                .isInstanceOf(IllegalStateException.class);
        verify(authService, never()).login(any(), any());
    }

    @Test
    void onAuthenticationSuccess_AuthService_예외_그대로_전파() {
        OAuth2User oAuth2User = givenOAuth2User();
        RuntimeException exception = new RuntimeException("login failed");
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(authService.login(any(OAuth2User.class), any())).willThrow(exception);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication))
                .isSameAs(exception);
        assertThat(response.getStatus()).isNotEqualTo(200);
    }

    @Test
    void onAuthenticationSuccess_isNewUser_false_케이스도_정상_직렬화() throws Exception {
        OAuth2User oAuth2User = givenOAuth2User();
        given(authentication.getPrincipal()).willReturn(oAuth2User);
        given(authService.login(any(OAuth2User.class), any()))
                .willReturn(new AuthTokenResponse("at", "rt", false));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        ApiResponse<AuthTokenResponse> body = objectMapper.readValue(
                response.getContentAsString(),
                new TypeReference<>() {
                }
        );
        assertThat(body.data().isNewUser()).isFalse();
    }

    private OAuth2User givenOAuth2User() {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "sub-123"),
                "sub"
        );
    }
}
