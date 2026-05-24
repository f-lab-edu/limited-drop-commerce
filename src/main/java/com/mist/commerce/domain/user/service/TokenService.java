package com.mist.commerce.domain.user.service;

import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.domain.user.entity.UserType;
import com.mist.commerce.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.util.StringUtils;
import io.netty.util.internal.StringUtil;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.boot.webmvc.autoconfigure.WebMvcProperties.Apiversion.Use;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final RefreshTokenRedisRepository redisRepository;
    private final SecretKey secretKey;

    public TokenService(JwtProperties jwtProperties, Clock clock, RefreshTokenRedisRepository redisRepository) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.redisRepository = redisRepository;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String userType, String status) {
        if (Objects.isNull(userId)) {
            throw new IllegalArgumentException("userId must not be null");
        }

        return Jwts.builder()
                .claim("userId", userId)
                .claim("userType", userType)
                .claim("status", status)
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusMillis(jwtProperties.accessTokenExpiry())))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public RefreshTokenPair generateRefreshToken(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        long ttlSeconds = jwtProperties.refreshTokenExpiry() / 1000;
        redisRepository.save(sessionId, value, ttlSeconds);
        return new RefreshTokenPair(sessionId, value);
    }

    public boolean validateToken(String token) {
        if (token.isBlank()) {
            return false;
        }

        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            Number userId = claims.get("userId", Number.class);
            if (Objects.isNull(userId)) {
                throw new InvalidTokenException("Token does not contain userId claim");
            }
            return userId.longValue();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(e);
        }
    }

    public String getUserTypeFromToken(String token) {
        try {
            Claims claims = parseClaims(token);
            String userType = claims.get("userType", String.class);
            return StringUtils.isBlank(userType) ? UserType.USER.name() : userType;
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException(e);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
