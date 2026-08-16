package com.mist.commerce.domain.payment.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mist.commerce.common.idempotency.model.IdempotencyRequest;
import com.mist.commerce.domain.payment.application.support.PaymentFingerprintGenerator;
import com.mist.commerce.domain.payment.dto.PaymentCommand;
import com.mist.commerce.global.web.CachedBodyHttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
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

class PaymentIdempotencyResolverTest {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final String PAYMENT_KEY = "pay_key_001";
    private static final BigDecimal AMOUNT = new BigDecimal("150000.00");
    private static final String IDEMPOTENCY_KEY = "idem-pay-001";
    private static final String BODY = """
            {"orderId":100,"paymentKey":"pay_key_001","paymentMethod":"CARD","amount":150000.00}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentFingerprintGenerator fingerprintGenerator = new PaymentFingerprintGenerator();
    private final PaymentIdempotencyResolver resolver =
            new PaymentIdempotencyResolver(objectMapper, fingerprintGenerator);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TC-PAY-IDEM-001: POST /api/v1/payments/pay 요청을 지원 대상으로 판단한다")
    void supports_whenPostPaymentsPay_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/pay");

        assertThat(resolver.supports(request)).isTrue();
    }

    @Test
    @DisplayName("TC-PAY-IDEM-002: 메서드가 다르면 지원 대상이 아니다")
    void supports_whenNotPost_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments/pay");

        assertThat(resolver.supports(request)).isFalse();
    }

    @Test
    @DisplayName("TC-PAY-IDEM-003: URI가 다르면 지원 대상이 아니다")
    void supports_whenDifferentUri_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");

        assertThat(resolver.supports(request)).isFalse();
    }

    @Test
    @DisplayName("TC-PAY-IDEM-004: 헤더의 멱등키·인증 사용자·본문으로 IdempotencyRequest를 만든다")
    void resolve_buildsIdempotencyRequestFromHeaderAuthAndBody() throws IOException {
        authenticate(USER_ID);
        CachedBodyHttpServletRequest request = cachedRequest(BODY, IDEMPOTENCY_KEY);

        IdempotencyRequest result = resolver.resolve(request);

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.scope()).isEqualTo("payment");
        assertThat(result.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(result.fingerprint()).isEqualTo(expectedFingerprint());
    }

    @Test
    @DisplayName("TC-PAY-IDEM-005: 인증 정보가 없으면 IllegalStateException이 발생한다")
    void resolve_whenAuthenticationMissing_throwsIllegalStateException() throws IOException {
        CachedBodyHttpServletRequest request = cachedRequest(BODY, IDEMPOTENCY_KEY);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인증 정보가 없습니다.");
    }

    @Test
    @DisplayName("TC-PAY-IDEM-006: principal 타입이 다르면 IllegalStateException이 발생한다")
    void resolve_whenPrincipalTypeUnsupported_throwsIllegalStateException() throws IOException {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "plain-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        CachedBodyHttpServletRequest request = cachedRequest(BODY, IDEMPOTENCY_KEY);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지원하지 않는 인증 principal 타입입니다");
    }

    private String expectedFingerprint() {
        PaymentCommand command = PaymentCommand.builder()
                .userId(USER_ID)
                .orderId(ORDER_ID)
                .paymentKey(PAYMENT_KEY)
                .amount(AMOUNT)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
        return fingerprintGenerator.generate(command);
    }

    private CachedBodyHttpServletRequest cachedRequest(String body, String idempotencyKey) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments/pay");
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
