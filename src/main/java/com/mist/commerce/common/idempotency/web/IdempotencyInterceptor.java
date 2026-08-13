package com.mist.commerce.common.idempotency.web;

import com.mist.commerce.common.idempotency.model.ClaimResult;
import com.mist.commerce.common.idempotency.model.IdempotencyContext;
import com.mist.commerce.common.idempotency.model.IdempotencyRequest;
import com.mist.commerce.common.idempotency.port.IdempotencyStore;
import com.mist.commerce.global.util.ResponseBodyUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final List<IdempotencyRequestResolver> resolvers;
    private final IdempotencyStore idempotencyStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        IdempotencyRequestResolver resolver = findResolver(request);

        if (resolver == null) {
            return true;
        }

        // claim 전에 검증 — 여기서 실패하면 남는 키가 없다
        validateResponseWrapper(response);

        IdempotencyRequest idempotencyRequest = resolver.resolve(request);
        String redisKey = idempotencyRequest.generate();

        ClaimResult claimResult = idempotencyStore.claim(
                idempotencyRequest.userId(),
                redisKey,
                idempotencyRequest.fingerprint(),
                idempotencyRequest.ttl());

        return handleClaimResult(request, response, idempotencyRequest, redisKey, claimResult);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) throws Exception {
        IdempotencyContext context = IdempotencyContextHolder.get(request);

        // 멱등성 대상이 아니거나, preHandle에서 CLAIMED 되지 않은 요청
        if (context == null) {
            return;
        }

        try {
            handleCompletion(context, response, ex);
        } catch (Exception e) {
            log.error("Failed to process idempotency afterCompletion. context={}", context, e);
        }
    }

    private boolean handleClaimResult(
            HttpServletRequest request,
            HttpServletResponse response,
            IdempotencyRequest idempotencyRequest,
            String redisKey,
            ClaimResult claimResult) throws IOException {

        return switch (claimResult.status()) {
            case COMPLETED -> {
                writeCompletedResponse(response, claimResult.resultPayload());
                yield false;
            }

            case IN_PROGRESS -> {
                writeConflict(response, "Request is already in progress.");
                yield false;
            }

            case MISMATCH -> {
                writeConflict(
                        response,
                        "Idempotency-Key was reused with different request."
                );
                yield false;
            }

            case CLAIMED -> {
                IdempotencyContext context = new IdempotencyContext(idempotencyRequest.userId()
                        , redisKey, idempotencyRequest.fingerprint());

                IdempotencyContextHolder.set(request, context);
                yield true;
            }
        };
    }

    private void handleCompletion(IdempotencyContext context, HttpServletResponse response, @Nullable Exception ex) {

        if (isFailed(response, ex)) {
            release(context);
            return;
        }

        String responseBody = ResponseBodyUtils.readCachedBody(response);

        if (responseBody.isBlank()) {
            log.warn("Idempotency response body is empty. release key. redisKey={}", context.redisKey());

            release(context);
            return;
        }

        idempotencyStore.complete(context.userId(), context.redisKey(), context.fingerprint(), responseBody);
    }

    private void writeCompletedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(message);
    }

    private void writeConflict(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(message);
    }

    private void release(IdempotencyContext context) {
        idempotencyStore.release(
                context.userId(),
                context.redisKey()
        );
    }

    private void validateResponseWrapper(HttpServletResponse response) {
        if (WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class) == null) {
            throw new IllegalStateException(
                    "ContentCachingResponseWrapper is required for idempotent requests. Check CachedBodyFilter registration.");
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
