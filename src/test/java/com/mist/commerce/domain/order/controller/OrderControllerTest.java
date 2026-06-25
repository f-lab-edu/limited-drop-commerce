package com.mist.commerce.domain.order.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mist.commerce.domain.order.entity.OrderStatus;
import com.mist.commerce.domain.order.exception.OrderAlreadyCancelledException;
import com.mist.commerce.domain.order.exception.OrderCancelTemporarilyUnavailableException;
import com.mist.commerce.domain.order.exception.OrderCannotCancelException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.order.service.CancelCommand;
import com.mist.commerce.domain.order.service.CancelResult;
import com.mist.commerce.domain.order.service.OrderCancelService;
import com.mist.commerce.domain.user.service.CustomOAuth2UserService;
import com.mist.commerce.domain.user.service.TokenService;
import com.mist.commerce.global.config.OAuth2LoginFailureHandler;
import com.mist.commerce.global.config.OAuth2LoginSuccessHandler;
import com.mist.commerce.global.config.SecurityConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderControllerTest.FixedClockConfig.class})
class OrderControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "order-cancel-idem-key-001";
    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 6, 17, 12, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderCancelService orderCancelService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Test
    @DisplayName("TC-OC-CTL-01: 인증된 사용자가 필수 헤더로 취소 요청하면 200과 취소 응답을 반환한다")
    void cancel_withAuthenticatedUserAndIdempotencyKey_returns200AndSuccessEnvelope() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class)))
                .willReturn(new CancelResult(ORDER_ID, OrderStatus.CANCELLED.name(), CANCELLED_AT));

        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .with(authentication(authenticatedUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").value("2026-06-17T12:00:00"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").value("2026-06-17T03:00:00.000Z"));

        ArgumentCaptor<CancelCommand> captor = ArgumentCaptor.forClass(CancelCommand.class);
        verify(orderCancelService).cancel(captor.capture());
        CancelCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.orderId()).isEqualTo(ORDER_ID);
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("TC-OC-CTL-02: 미로그인 요청은 401 UNAUTHORIZED를 반환하고 서비스를 호출하지 않는다")
    void cancel_withoutAuthentication_returns401AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(orderCancelService, never()).cancel(any());
    }

    @Test
    @DisplayName("TC-OC-CTL-02: Idempotency-Key 헤더가 없으면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void cancel_withoutIdempotencyKeyHeader_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                        .with(authentication(authenticatedUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'Idempotency-Key')]").exists());

        verify(orderCancelService, never()).cancel(any());
    }

    @Test
    @DisplayName("TC-OC-CTL-03: ORDER_NOT_FOUND는 404로 반환된다")
    void cancel_whenServiceThrowsOrderNotFound_returns404() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class))).willThrow(new OrderNotFoundException());

        mockMvc.perform(authenticatedCancelRequest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-OC-CTL-03: ORDER_FORBIDDEN은 403으로 반환된다")
    void cancel_whenServiceThrowsOrderForbidden_returns403() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class))).willThrow(new OrderForbiddenException());

        mockMvc.perform(authenticatedCancelRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-OC-CTL-03: ORDER_ALREADY_CANCELLED는 409로 반환된다")
    void cancel_whenServiceThrowsOrderAlreadyCancelled_returns409() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class))).willThrow(new OrderAlreadyCancelledException());

        mockMvc.perform(authenticatedCancelRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_CANCELLED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-OC-CTL-03: ORDER_CANNOT_CANCEL은 409로 반환된다")
    void cancel_whenServiceThrowsOrderCannotCancel_returns409() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class))).willThrow(new OrderCannotCancelException());

        mockMvc.perform(authenticatedCancelRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_CANNOT_CANCEL"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-OC-CTL-03: ORDER_CANCEL_TEMPORARILY_UNAVAILABLE은 409로 반환된다")
    void cancel_whenServiceThrowsTemporarilyUnavailable_returns409() throws Exception {
        given(orderCancelService.cancel(any(CancelCommand.class)))
                .willThrow(new OrderCancelTemporarilyUnavailableException());

        mockMvc.perform(authenticatedCancelRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_CANCEL_TEMPORARILY_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private org.springframework.test.web.servlet.RequestBuilder authenticatedCancelRequest() {
        return post("/api/v1/orders/{orderId}/cancel", ORDER_ID)
                .with(authentication(authenticatedUser()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY);
    }

    private UsernamePasswordAuthenticationToken authenticatedUser() {
        return new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-17T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
