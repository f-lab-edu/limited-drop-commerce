package com.mist.commerce.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingResponseWrapper;

@ExtendWith(MockitoExtension.class)
class IdempotencyInterceptorTest {

    private static final Long USER_ID = 10L;
    private static final String USER_ID_ATTR = "10";
    private static final String REDIS_KEY = "reservation:idem-key-001";
    private static final String FINGERPRINT = "fingerprint-abc";
    private static final String PAYLOAD = "{\"orderId\":1000}";

    @Mock
    private IdempotencyRequestResolver resolver;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private IdempotencyKeyGenerator keyGenerator;

    private IdempotencyInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new IdempotencyInterceptor(List.of(resolver), idempotencyStore, keyGenerator);
        request = new MockHttpServletRequest("POST", "/api/v1/reservations");
        response = new MockHttpServletResponse();
    }

    // --- preHandle ---------------------------------------------------------

    @Test
    @DisplayName("멱등성 대상 리졸버가 없으면 요청을 통과시키고 claim을 하지 않는다")
    void preHandle_whenNoResolverSupports_passesThroughWithoutClaim() throws Exception {
        when(resolver.supports(request)).thenReturn(false);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        verifyNoInteractions(keyGenerator, idempotencyStore);
    }

    @Test
    @DisplayName("CLAIMED이면 요청을 통과시키고 afterCompletion용 속성을 채운다")
    void preHandle_whenClaimed_proceedsAndStoresAttributes() throws Exception {
        givenClaim(new ClaimResult(ClaimStatus.CLAIMED, null));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_USER_ID)).isEqualTo(USER_ID_ATTR);
        assertThat(request.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_REDIS_KEY)).isEqualTo(REDIS_KEY);
        assertThat(request.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_FINGERPRINT)).isEqualTo(FINGERPRINT);
    }

    @Test
    @DisplayName("COMPLETED이면 저장된 결과 본문을 그대로 응답하고 요청을 중단한다")
    void preHandle_whenCompleted_writesStoredPayloadAndStops() throws Exception {
        givenClaim(new ClaimResult(ClaimStatus.COMPLETED, PAYLOAD));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("IN_PROGRESS이면 409로 요청을 중단한다")
    void preHandle_whenInProgress_respondsConflictAndStops() throws Exception {
        givenClaim(new ClaimResult(ClaimStatus.IN_PROGRESS, null));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("Request is already in progress.");
    }

    @Test
    @DisplayName("MISMATCH이면 409로 요청을 중단한다")
    void preHandle_whenMismatch_respondsConflictAndStops() throws Exception {
        givenClaim(new ClaimResult(ClaimStatus.MISMATCH, null));

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).isEqualTo("Idempotency-Key was reused with different request.");
    }

    // --- afterCompletion ---------------------------------------------------

    @Test
    @DisplayName("멱등성 속성이 없으면(대상이 아니면) store를 건드리지 않는다")
    void afterCompletion_whenNotClaimedRequest_doesNothing() throws Exception {
        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(idempotencyStore);
    }

    @Test
    @DisplayName("정상 응답이면 응답 본문으로 멱등키를 완료 처리한다")
    void afterCompletion_whenSuccess_completesWithResponseBody() throws Exception {
        markClaimed(request);
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        wrapper.getWriter().write(PAYLOAD);
        wrapper.getWriter().flush();

        interceptor.afterCompletion(request, wrapper, new Object(), null);

        verify(idempotencyStore).complete(USER_ID, REDIS_KEY, FINGERPRINT, PAYLOAD);
        verify(idempotencyStore, never()).release(any(), any());
    }

    @Test
    @DisplayName("예외가 발생하면 멱등키를 해제한다")
    void afterCompletion_whenExceptionThrown_releasesKey() throws Exception {
        markClaimed(request);

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        verify(idempotencyStore).release(USER_ID, REDIS_KEY);
        verify(idempotencyStore, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("2xx가 아닌 상태 코드면 멱등키를 해제한다")
    void afterCompletion_whenNonSuccessStatus_releasesKey() throws Exception {
        markClaimed(request);
        response.setStatus(500);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(idempotencyStore).release(USER_ID, REDIS_KEY);
        verify(idempotencyStore, never()).complete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 멱등키를 해제한다")
    void afterCompletion_whenResponseBodyEmpty_releasesKey() throws Exception {
        markClaimed(request);
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);

        interceptor.afterCompletion(request, wrapper, new Object(), null);

        verify(idempotencyStore).release(USER_ID, REDIS_KEY);
        verify(idempotencyStore, never()).complete(any(), any(), any(), any());
    }

    private void givenClaim(ClaimResult claimResult) throws Exception {
        IdempotencyRequest idempotencyRequest = IdempotencyRequest.builder()
                .userId(USER_ID)
                .scope("reservation")
                .idempotencyKey("idem-key-001")
                .fingerprint(FINGERPRINT)
                .build();
        when(resolver.supports(request)).thenReturn(true);
        when(resolver.resolve(request)).thenReturn(idempotencyRequest);
        when(keyGenerator.generate(idempotencyRequest)).thenReturn(REDIS_KEY);
        when(idempotencyStore.claim(USER_ID, REDIS_KEY, FINGERPRINT, ReservePolicy.PAYMENT_TTL))
                .thenReturn(claimResult);
    }

    private void markClaimed(MockHttpServletRequest request) {
        request.setAttribute(IdempotencyInterceptor.IDEMPOTENCY_USER_ID, USER_ID_ATTR);
        request.setAttribute(IdempotencyInterceptor.IDEMPOTENCY_REDIS_KEY, REDIS_KEY);
        request.setAttribute(IdempotencyInterceptor.IDEMPOTENCY_FINGERPRINT, FINGERPRINT);
    }
}
