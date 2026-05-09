package com.mist.commerce.global.config;

import com.mist.commerce.domain.user.exception.OAuthAccountAlreadyLinkedToBusinessException;
import com.mist.commerce.domain.user.exception.UserEmailDuplicatedException;
import com.mist.commerce.global.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        ErrorResponse errorResponse = resolveErrorResponse(exception);
        response.setStatus(errorResponse.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.fail(errorResponse.code(), errorResponse.message(), clock.instant())
        );
    }

    private ErrorResponse resolveErrorResponse(AuthenticationException exception) {
        Throwable cause = findDomainCause(exception);
        if (cause instanceof UserEmailDuplicatedException duplicatedException) {
            return new ErrorResponse(
                    UserEmailDuplicatedException.CODE,
                    duplicatedException.getMessage(),
                    UserEmailDuplicatedException.HTTP_STATUS
            );
        }
        if (cause instanceof OAuthAccountAlreadyLinkedToBusinessException linkedException) {
            return new ErrorResponse(
                    OAuthAccountAlreadyLinkedToBusinessException.CODE,
                    linkedException.getMessage(),
                    OAuthAccountAlreadyLinkedToBusinessException.HTTP_STATUS
            );
        }
        if (exception instanceof OAuth2AuthenticationException) {
            return new ErrorResponse(
                    "UNAUTHORIZED",
                    "OAuth2 authentication failed",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Internal server error",
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    private Throwable findDomainCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UserEmailDuplicatedException
                    || current instanceof OAuthAccountAlreadyLinkedToBusinessException) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    private record ErrorResponse(String code, String message, HttpStatus status) {
    }
}
