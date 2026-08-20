package com.mist.commerce.support;

import com.mist.commerce.common.idempotency.port.IdempotencyStore;
import com.mist.commerce.domain.user.service.CustomOAuth2UserService;
import com.mist.commerce.domain.user.service.TokenService;
import com.mist.commerce.global.config.OAuth2LoginFailureHandler;
import com.mist.commerce.global.config.OAuth2LoginSuccessHandler;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class ControllerTestSupport {

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @MockitoBean
    private IdempotencyStore idempotencyStore;

    protected UsernamePasswordAuthenticationToken authenticatedUser(Long principalId, String authority) {
        return new UsernamePasswordAuthenticationToken(
                principalId,
                null,
                List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
