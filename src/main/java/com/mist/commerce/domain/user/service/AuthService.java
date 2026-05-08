package com.mist.commerce.domain.user.service;

import com.mist.commerce.domain.user.dto.AuthTokenResponse;
import com.mist.commerce.domain.user.entity.LoginHistory;
import com.mist.commerce.domain.user.entity.LoginType;
import com.mist.commerce.domain.user.entity.RefreshTokenSession;
import com.mist.commerce.domain.user.entity.SessionStatus;
import com.mist.commerce.domain.user.repository.LoginHistoryRepository;
import com.mist.commerce.domain.user.repository.RefreshTokenSessionRepository;
import com.mist.commerce.global.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final TokenService tokenService;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthTokenResponse login(OAuth2User oAuth2User, HttpServletRequest request) {
        if (oAuth2User == null || request == null) {
            throw new IllegalArgumentException("oAuth2User and request must not be null");
        }

        Long userId = extractUserId(oAuth2User);
        String userType = requiredAttribute(oAuth2User, "userType", String.class);
        String status = requiredAttribute(oAuth2User, "status", String.class);
        Boolean isNewUser = requiredAttribute(oAuth2User, "isNewUser", Boolean.class);
        String ipAddress = resolveIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        String accessToken = tokenService.generateAccessToken(userId, userType, status);
        RefreshTokenPair refreshTokenPair = tokenService.generateRefreshToken(userId);
        LocalDateTime expiredAt = LocalDateTime.now(clock)
                .plus(jwtProperties.refreshTokenExpiry(), ChronoUnit.MILLIS);

        refreshTokenSessionRepository.save(RefreshTokenSession.of(
                userId,
                refreshTokenPair.sessionId(),
                SessionStatus.ACTIVE,
                expiredAt,
                ipAddress,
                userAgent,
                null,
                null
        ));
        loginHistoryRepository.save(LoginHistory.success(userId, LoginType.GOOGLE, ipAddress, userAgent));

        return new AuthTokenResponse(accessToken, refreshTokenPair.value(), isNewUser);
    }

    private Long extractUserId(OAuth2User oAuth2User) {
        Number userId = requiredAttribute(oAuth2User, "userId", Number.class);
        return userId.longValue();
    }

    private <T> T requiredAttribute(OAuth2User oAuth2User, String key, Class<T> type) {
        T value = oAuth2User.getAttribute(key);
        if (value == null) {
            throw new IllegalStateException("Missing OAuth2 attribute: " + key);
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Invalid OAuth2 attribute type: " + key);
        }
        return value;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
