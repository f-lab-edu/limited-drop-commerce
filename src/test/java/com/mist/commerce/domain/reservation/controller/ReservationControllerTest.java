package com.mist.commerce.domain.reservation.controller;

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

import com.mist.commerce.domain.event.exception.StockExhaustedException;
import com.mist.commerce.domain.reservation.dto.ReservationRequest;
import com.mist.commerce.domain.reservation.service.ReservationService;
import com.mist.commerce.domain.reservation.service.ReserveCommand;
import com.mist.commerce.domain.reservation.service.ReserveResult;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, ReservationControllerTest.FixedClockConfig.class})
class ReservationControllerTest {

    private static final Long USER_ID = 10L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 6, 19, 13, 30);
    private static final String IDEMPOTENCY_KEY = "reservation-idem-key-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Test
    @DisplayName("TC-RES-CTRL-IDEM-001: Idempotency-Key 헤더 포함 정상 요청은 command에 idempotencyKey를 싣는다")
    void reserve_withAuthenticatedUserAndValidBody_returns201AndSuccessEnvelope() throws Exception {
        given(reservationService.reserve(any(ReserveCommand.class)))
                .willReturn(new ReserveResult(1000L, EXPIRES_AT, "PENDING_PAYMENT"));

        mockMvc.perform(post("/api/v1/reservations")
                        .with(authentication(authenticatedUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("리소스가 생성되었습니다."))
                .andExpect(jsonPath("$.data.orderId").value(1000))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-06-19T13:30:00"))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").value("2026-06-19T03:00:00.000Z"));

        ArgumentCaptor<ReserveCommand> captor = ArgumentCaptor.forClass(ReserveCommand.class);
        verify(reservationService).reserve(captor.capture());
        ReserveCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.eventId()).isEqualTo(20L);
        assertThat(command.eventItemId()).isEqualTo(30L);
        assertThat(command.eventItemOptionStockId()).isEqualTo(40L);
        assertThat(command.quantity()).isEqualTo(2);
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("TC-RES-CTRL-IDEM-002: Idempotency-Key 헤더가 없으면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void reserve_withoutIdempotencyKeyHeader_returns400ValidationErrorAndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .with(authentication(authenticatedUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").value(nullValue()));

        verify(reservationService, never()).reserve(any());
    }

    @Test
    @DisplayName("quantity가 0이면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void reserve_withZeroQuantity_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .with(authentication(authenticatedUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 20,
                                  "eventItemId": 30,
                                  "eventItemOptionStockId": 40,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'quantity')]").exists());

        verify(reservationService, never()).reserve(any());
    }

    @Test
    @DisplayName("필수 필드가 null이면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void reserve_withNullRequiredField_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .with(authentication(authenticatedUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 20,
                                  "eventItemId": 30,
                                  "eventItemOptionStockId": null,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'eventItemOptionStockId')]").exists());

        verify(reservationService, never()).reserve(any());
    }

    @Test
    @DisplayName("서비스가 STOCK_EXHAUSTED를 던지면 400과 STOCK_EXHAUSTED envelope를 반환한다")
    void reserve_whenServiceThrowsStockExhausted_returns400StockExhausted() throws Exception {
        given(reservationService.reserve(any(ReserveCommand.class)))
                .willThrow(new StockExhaustedException());

        mockMvc.perform(post("/api/v1/reservations")
                        .with(authentication(authenticatedUser()))
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("STOCK_EXHAUSTED"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    @DisplayName("인증 없이 예약을 요청하면 401을 반환하고 서비스를 호출하지 않는다")
    void reserve_withoutAuthentication_returns401AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(reservationService, never()).reserve(any());
    }

    private ReservationRequest validRequest() {
        return new ReservationRequest(20L, 30L, 40L, 2);
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
            return Clock.fixed(Instant.parse("2026-06-19T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
