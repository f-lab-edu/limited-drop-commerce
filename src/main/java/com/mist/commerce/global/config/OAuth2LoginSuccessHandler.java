package com.mist.commerce.global.config;

import com.mist.commerce.domain.user.dto.AuthTokenResponse;
import com.mist.commerce.domain.user.service.AuthService;
import com.mist.commerce.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String LOGIN_SUCCESS_MESSAGE = "로그인에 성공했습니다.";

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        clearAuthenticationAttributes(request);
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OAuth2User oAuth2User)) {
            throw new IllegalStateException("OAuth2 principal must be OAuth2User");
        }

        AuthTokenResponse authTokenResponse;
        try {
            authTokenResponse = authService.login(oAuth2User, request);
        } catch (RuntimeException e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            throw e;
        }

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.success(authTokenResponse, LOGIN_SUCCESS_MESSAGE, clock.instant())
        );
    }
}
