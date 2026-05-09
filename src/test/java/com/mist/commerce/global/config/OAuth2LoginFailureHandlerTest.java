package com.mist.commerce.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class OAuth2LoginFailureHandlerTest {

    ObjectMapper objectMapper;
    OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new OAuth2LoginFailureHandler(
                objectMapper,
                java.time.Clock.fixed(java.time.Instant.parse("2026-05-09T10:00:00Z"), java.time.ZoneOffset.UTC)
        );
    }

    @Test
    void onAuthenticationFailure_UserEmailDuplicatedException_원인이면_409_USER_EMAIL_DUPLICATED() throws Exception {
        OAuth2AuthenticationException exception = oauthException(
                new UserEmailDuplicatedException("a@b.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        ApiResponse<Object> body = readBody(response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(body.success()).isFalse();
        assertThat(body.code()).isEqualTo("USER_EMAIL_DUPLICATED");
        assertThat(body.errors()).isNull();
        assertThat(body.message()).contains("a@b.com");
    }

    @Test
    void onAuthenticationFailure_OAuthAccountAlreadyLinkedToBusinessException_원인이면_409() throws Exception {
        OAuth2AuthenticationException exception = oauthException(
                new OAuthAccountAlreadyLinkedToBusinessException());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        ApiResponse<Object> body = readBody(response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("OAUTH_ACCOUNT_ALREADY_LINKED_TO_BUSINESS");
        assertThat(body.errors()).isNull();
    }

    @Test
    void onAuthenticationFailure_OAuth2AuthenticationException_cause_없으면_400_UNAUTHORIZED() throws Exception {
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_request"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        ApiResponse<Object> body = readBody(response);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void onAuthenticationFailure_미분류_예외이면_500_INTERNAL_SERVER_ERROR() throws Exception {
        AuthenticationException exception = new AuthenticationServiceException(
                "unknown", new RuntimeException("unknown"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        ApiResponse<Object> body = readBody(response);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.errors()).isNull();
    }

    @Test
    void onAuthenticationFailure_응답_Content_Type_application_json() throws Exception {
        OAuth2AuthenticationException exception = oauthException(
                new UserEmailDuplicatedException("a@b.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertThat(response.getContentType()).contains("application/json");
    }

    @Test
    void onAuthenticationFailure_cause_체인_재귀_unwrap() throws Exception {
        OAuth2AuthenticationException exception = oauthException(
                new RuntimeException("wrapper", new UserEmailDuplicatedException("a@b.com")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        ApiResponse<Object> body = readBody(response);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("USER_EMAIL_DUPLICATED");
    }

    @Test
    void onAuthenticationFailure_response_body_errors_null_보장() throws Exception {
        OAuth2AuthenticationException exception = oauthException(
                new UserEmailDuplicatedException("a@b.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertThat(readBody(response).errors()).isNull();
    }

    private OAuth2AuthenticationException oauthException(Throwable cause) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("oauth2_user_load_failed"),
                "OAuth2 user load failed",
                cause
        );
    }

    private ApiResponse<Object> readBody(MockHttpServletResponse response) throws Exception {
        return objectMapper.readValue(
                response.getContentAsString(),
                new TypeReference<>() {
                }
        );
    }
}
