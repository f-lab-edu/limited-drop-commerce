package com.mist.commerce.common.idempotency;

import com.mist.commerce.domain.reservation.dto.ReservePolicy;
import com.mist.commerce.global.util.ResponseBodyUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    public static final String IDEMPOTENCY_USER_ID = "idempotency.userId";
    public static final String IDEMPOTENCY_REDIS_KEY = "idempotency.redisKey";
    public static final String IDEMPOTENCY_FINGERPRINT = "idempotency.fingerprint";
    private final List<IdempotencyRequestResolver> resolvers;
    private final IdempotencyStore idempotencyStore;
    private final IdempotencyKeyGenerator keyGenerator;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        IdempotencyRequestResolver resolver = findResolver(request);

        if (resolver == null) {
            return true;
        }

        // claim 전에 검증 — 여기서 실패하면 남는 키가 없다
        if (WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class) == null) {
            throw new IllegalStateException(
                    "ContentCachingResponseWrapper is required for idempotent requests. Check CachedBodyFilter registration.");
        }

        IdempotencyRequest resolve = resolver.resolve(request);
        String redisKey = keyGenerator.generate(resolve);

        ClaimResult claimResult = idempotencyStore.claim(
                resolve.userId(),
                redisKey,
                resolve.fingerprint(),
                ReservePolicy.PAYMENT_TTL);

        if (claimResult.status() == ClaimStatus.COMPLETED) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(claimResult.resultPayload());
            return false;
        }

        if (claimResult.status() == ClaimStatus.IN_PROGRESS) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Request is already in progress.");
            return false;
        }

        if (claimResult.status() == ClaimStatus.MISMATCH) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            response.getWriter().write("Idempotency-Key was reused with different request.");
            return false;
        }

        new IdempotencyContext(resolve.userId(), redisKey, resolve.fingerprint())
                .writeTo(request);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) throws Exception {
        IdempotencyContext context = IdempotencyContext.from(request);

        // 멱등성 대상이 아니거나, preHandle에서 CLAIMED 되지 않은 요청
        if (context == null) {
            return;
        }

        try {

            if (isFailed(response, ex)) {
                idempotencyStore.release(context.userId(), context.redisKey());
                return;
            }

            String responseBody = ResponseBodyUtils.readCachedBody(response);

            if (responseBody.isBlank()) {
                log.warn("Idempotency response body is empty. release key. redisKey={}", context.redisKey());
                idempotencyStore.release(context.userId(), context.redisKey());
                return;
            }

            idempotencyStore.complete(context.userId(), context.redisKey(), context.fingerprint(), responseBody);
        } catch (Exception e) {
            log.error("Failed to process idempotency afterCompletion. context={}", context, e);
        }
    }

    private IdempotencyRequestResolver findResolver(HttpServletRequest request) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(request))
                .findFirst()
                .orElse(null);
    }

    private boolean isFailed(HttpServletResponse response, Exception ex) {
        return ex != null
                || response.getStatus() < 200
                || response.getStatus() >= 300;
    }
}
