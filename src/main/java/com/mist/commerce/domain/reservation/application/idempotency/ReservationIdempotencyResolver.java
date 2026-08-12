package com.mist.commerce.domain.reservation.application.idempotency;

import com.mist.commerce.common.idempotency.model.IdempotencyRequest;
import com.mist.commerce.common.idempotency.web.IdempotencyRequestResolver;
import com.mist.commerce.domain.reservation.application.support.ReserveFingerprintGenerator;
import com.mist.commerce.domain.reservation.dto.ReservationRequest;
import com.mist.commerce.domain.reservation.dto.ReserveCommand;
import com.mist.commerce.global.web.CachedBodyHttpServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ReservationIdempotencyResolver implements IdempotencyRequestResolver {

    private final ObjectMapper objectMapper;
    private final ReserveFingerprintGenerator fingerprintGenerator;

    @Override
    public boolean supports(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "/api/v1/reservations".equals(request.getRequestURI());
    }

    @Override
    public IdempotencyRequest resolve(HttpServletRequest request) throws IOException {
        String idempotencyKey = request.getHeader("Idempotency-Key");
        Long userId = extractUserId();

        String body = readBody(request);
        ReservationRequest reservationRequest = objectMapper.readValue(body, ReservationRequest.class);

        ReserveCommand command = ReserveCommand.builder()
                .userId(userId)
                .eventId(reservationRequest.eventId())
                .eventItemId(reservationRequest.eventItemId())
                .eventItemOptionStockId(reservationRequest.eventItemOptionStockId())
                .quantity(reservationRequest.quantity())
                .idempotencyKey(idempotencyKey)
                .build();

        String fingerprint = fingerprintGenerator.generate(command);

        return IdempotencyRequest.builder()
                .userId(userId)
                .scope("reservation")
                .idempotencyKey(idempotencyKey)
                .fingerprint(fingerprint)
                .build();
    }

    private Long extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof DefaultOAuth2User userDetails) {
            return userDetails.getAttribute("userId");
        }

        throw new IllegalStateException("지원하지 않는 인증 principal 타입입니다: " + principal.getClass());
    }

    private String readBody(HttpServletRequest request) {
        CachedBodyHttpServletRequest cachedRequest = (CachedBodyHttpServletRequest) request;
        return cachedRequest.getCachedBodyAsString();
    }
}
