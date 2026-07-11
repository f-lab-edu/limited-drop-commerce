package com.mist.commerce.domain.reservation.application.Idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.mist.commerce.common.idempotency.IdempotencyRequest;
import com.mist.commerce.domain.reservation.application.support.ReserveFingerprintGenerator;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.global.web.CachedBodyHttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import tools.jackson.databind.ObjectMapper;

class ReservationIdempotencyResolverTest {

    private static final Long USER_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final String BODY = """
            {"eventId":20,"eventItemId":30,"eventItemOptionStockId":40,"quantity":2}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReserveFingerprintGenerator fingerprintGenerator = new ReserveFingerprintGenerator();
    private final ReservationIdempotencyResolver resolver =
            new ReservationIdempotencyResolver(objectMapper, fingerprintGenerator);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/reservations 요청을 지원 대상으로 판단한다")
    void supports_whenPostReservations_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/reservations");

        assertThat(resolver.supports(request)).isTrue();
    }

    @Test
    @DisplayName("메서드가 다르면 지원 대상이 아니다")
    void supports_whenNotPost_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/reservations");

        assertThat(resolver.supports(request)).isFalse();
    }

    @Test
    @DisplayName("URI가 다르면 지원 대상이 아니다")
    void supports_whenDifferentUri_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");

        assertThat(resolver.supports(request)).isFalse();
    }

    @Test
    @DisplayName("헤더의 멱등키·인증 사용자·본문으로 IdempotencyRequest를 만든다")
    void resolve_buildsIdempotencyRequestFromHeaderAuthAndBody() throws IOException {
        authenticate(USER_ID);
        CachedBodyHttpServletRequest request = cachedRequest(BODY, IDEMPOTENCY_KEY);

        IdempotencyRequest result = resolver.resolve(request);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.scope()).isEqualTo("reservation");
        assertThat(result.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(result.fingerprint()).isEqualTo(expectedFingerprint());
    }

    private String expectedFingerprint() {
        ReserveCommand command = ReserveCommand.builder()
                .userId(USER_ID)
                .eventId(20L)
                .eventItemId(30L)
                .eventItemOptionStockId(40L)
                .quantity(2)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
        return fingerprintGenerator.generate(command);
    }

    private CachedBodyHttpServletRequest cachedRequest(String body, String idempotencyKey) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/reservations");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.addHeader("Idempotency-Key", idempotencyKey);
        return new CachedBodyHttpServletRequest(request);
    }

    private void authenticate(Long userId) {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("userId", userId, "sub", "sub-" + userId),
                "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
