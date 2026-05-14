package com.mist.commerce.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.mist.commerce.domain.user.exception.InvalidTokenException;
import com.mist.commerce.global.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    RefreshTokenRedisRepository redisRepository;

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-05-07T10:00:00Z"), ZoneOffset.UTC);
    private final String secret = "test-secret-min-32-bytes-aaaaaaaaaaaaaa";
    private final long accessExpiryMs = 1_800_000L;
    private final long refreshExpiryMs = 1_209_600_000L;

    private TokenService service;

    @BeforeEach
    void setUp() {
        service = new TokenService(
                new JwtProperties(secret, accessExpiryMs, refreshExpiryMs),
                fixedClock,
                redisRepository
        );
    }

    @Test
    void generateAccessToken_payload에_userId_userType_status_iat_exp_포함() {
        String token = service.generateAccessToken(1L, "USER", "ACTIVE");

        Claims claims = parse(token, secret);

        assertThat(claims.get("userId", Number.class).longValue()).isEqualTo(1L);
        assertThat(claims.get("userType", String.class)).isEqualTo("USER");
        assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(fixedClock.instant()));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(fixedClock.instant().plusMillis(accessExpiryMs)));
    }

    @Test
    void generateAccessToken_HS256_서명_적용() {
        String token = service.generateAccessToken(1L, "USER", "ACTIVE");

        assertThat(parse(token, secret).get("userId", Number.class).longValue()).isEqualTo(1L);
        assertThatThrownBy(() -> parse(token, "different-secret-min-32-bytes-aaaaaaaa"))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void generateAccessToken_userId_null이면_예외() {
        assertThatThrownBy(() -> service.generateAccessToken(null, "USER", "ACTIVE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateAccessToken_매번_iat_고정이면_동일_토큰() {
        String first = service.generateAccessToken(1L, "USER", "ACTIVE");
        String second = service.generateAccessToken(1L, "USER", "ACTIVE");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void generateRefreshToken_UUID_sessionId로_Redis에_저장() {
        RefreshTokenPair pair = service.generateRefreshToken(1L);

        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisRepository).save(sessionIdCaptor.capture(), tokenCaptor.capture(), ttlCaptor.capture());

        assertThat(sessionIdCaptor.getValue()).isEqualTo(pair.sessionId());
        assertThat(tokenCaptor.getValue()).isEqualTo(pair.value());
        assertThatCode(() -> java.util.UUID.fromString(sessionIdCaptor.getValue())).doesNotThrowAnyException();
    }

    @Test
    void generateRefreshToken_매_호출마다_고유한_sessionId() {
        RefreshTokenPair first = service.generateRefreshToken(1L);
        RefreshTokenPair second = service.generateRefreshToken(1L);

        assertThat(second.sessionId()).isNotEqualTo(first.sessionId());
    }

    @Test
    void generateRefreshToken_TTL_application_property와_일치() {
        service.generateRefreshToken(1L);

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisRepository).save(anyString(), anyString(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(1_209_600L);
    }

    @Test
    void generateRefreshToken_Redis_저장_실패시_예외_전파() {
        RedisConnectionFailureException exception = new RedisConnectionFailureException("redis down");
        willThrow(exception).given(redisRepository).save(anyString(), anyString(), anyLong());

        assertThatThrownBy(() -> service.generateRefreshToken(1L))
                .isSameAs(exception);
    }

    @Test
    void validateToken_유효한_토큰이면_true() {
        String token = service.generateAccessToken(1L, "USER", "ACTIVE");

        assertThat(service.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_만료_토큰이면_false() {
        String token = service.generateAccessToken(1L, "USER", "ACTIVE");
        TokenService laterService = new TokenService(
                new JwtProperties(secret, accessExpiryMs, refreshExpiryMs),
                Clock.fixed(fixedClock.instant().plusMillis(accessExpiryMs).plusSeconds(1), ZoneOffset.UTC),
                redisRepository
        );

        assertThat(laterService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_시그니처_위조이면_false() {
        String token = service.generateAccessToken(1L, "USER", "ACTIVE");

        assertThat(service.validateToken(tamper(token))).isFalse();
    }

    @Test
    void validateToken_형식_불량이면_false() {
        assertThat(service.validateToken("not.a.jwt")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void validateToken_null_또는_blank이면_false(String token) {
        assertThat(service.validateToken(token)).isFalse();
    }

    @Test
    void getUserIdFromToken_정상_토큰에서_userId_추출() {
        String token = service.generateAccessToken(42L, "USER", "ACTIVE");

        assertThat(service.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void getUserIdFromToken_시그니처_위조이면_InvalidTokenException() {
        String token = service.generateAccessToken(42L, "USER", "ACTIVE");

        assertThatThrownBy(() -> service.getUserIdFromToken(tamper(token)))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getUserIdFromToken_만료_토큰이면_InvalidTokenException() {
        String token = service.generateAccessToken(42L, "USER", "ACTIVE");
        TokenService laterService = new TokenService(
                new JwtProperties(secret, accessExpiryMs, refreshExpiryMs),
                Clock.fixed(fixedClock.instant().plusMillis(accessExpiryMs).plusSeconds(1), ZoneOffset.UTC),
                redisRepository
        );

        assertThatThrownBy(() -> laterService.getUserIdFromToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void getUserIdFromToken_userId_claim_없으면_InvalidTokenException() {
        String token = Jwts.builder()
                .claim("userType", "USER")
                .claim("status", "ACTIVE")
                .issuedAt(Date.from(fixedClock.instant()))
                .expiration(Date.from(fixedClock.instant().plusMillis(accessExpiryMs)))
                .signWith(secretKey(secret), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> service.getUserIdFromToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    private Claims parse(String token, String secret) {
        return Jwts.parser()
                .verifyWith(secretKey(secret))
                .clock(() -> Date.from(fixedClock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String tamper(String token) {
        String[] parts = token.split("\\.");
        char firstSignatureChar = parts[2].charAt(0);
        char replacement = firstSignatureChar == 'a' ? 'b' : 'a';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }
}
