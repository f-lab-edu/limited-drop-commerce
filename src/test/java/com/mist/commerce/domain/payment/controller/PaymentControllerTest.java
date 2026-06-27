package com.mist.commerce.domain.payment.controller;

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

import com.mist.commerce.domain.order.exception.OrderCannotPayException;
import com.mist.commerce.domain.order.exception.OrderForbiddenException;
import com.mist.commerce.domain.order.exception.OrderNotFoundException;
import com.mist.commerce.domain.payment.exception.PaymentAmountMismatchException;
import com.mist.commerce.domain.payment.exception.PaymentFailedException;
import com.mist.commerce.domain.payment.service.PaymentCommand;
import com.mist.commerce.domain.payment.service.PaymentResult;
import com.mist.commerce.domain.payment.service.PaymentService;
import com.mist.commerce.domain.user.service.CustomOAuth2UserService;
import com.mist.commerce.domain.user.service.TokenService;
import com.mist.commerce.global.config.OAuth2LoginFailureHandler;
import com.mist.commerce.global.config.OAuth2LoginSuccessHandler;
import com.mist.commerce.global.config.SecurityConfig;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.test.web.servlet.RequestBuilder;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, PaymentControllerTest.FixedClockConfig.class})
class PaymentControllerTest {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final String PAYMENT_KEY = "pay_key_001";
    private static final String PAYMENT_NO = "PAY-20260627-0001";
    private static final String IDEMPOTENCY_KEY = "payment-idem-key-001";
    private static final String AMOUNT = "150000.00";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Test
    @DisplayName("TC-PAY-CTL-001: 인증된 사용자가 필수 헤더와 유효 body로 결제 요청하면 200과 결제 완료 응답을 반환한다")
    void pay_withAuthenticatedUserAndValidRequest_returns200AndSuccessEnvelope() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class)))
                .willReturn(new PaymentResult(PAYMENT_ID, PAYMENT_NO, "PAID", "APPROVED"));

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("결제가 완료되었습니다."))
                .andExpect(jsonPath("$.data.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.data.paymentNo").value(PAYMENT_NO))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.data.paymentStatus").value("APPROVED"))
                .andExpect(jsonPath("$.errors").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").value("2026-06-27T03:00:00.000Z"));

        ArgumentCaptor<PaymentCommand> captor = ArgumentCaptor.forClass(PaymentCommand.class);
        verify(paymentService).pay(captor.capture());
        PaymentCommand command = captor.getValue();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.orderId()).isEqualTo(ORDER_ID);
        assertThat(command.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(command.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("TC-PAY-CTL-002: 미로그인 결제 요청은 401 UNAUTHORIZED를 반환하고 서비스를 호출하지 않는다")
    void pay_withoutAuthentication_returns401AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));

        verify(paymentService, never()).pay(any());
    }

    @Test
    @DisplayName("TC-PAY-CTL-003: Idempotency-Key 헤더가 없으면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void pay_withoutIdempotencyKeyHeader_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(authentication(authenticatedUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors[?(@.field == 'Idempotency-Key')]").exists())
                .andExpect(jsonPath("$.errors[?(@.reason == '필수 요청 헤더가 누락되었습니다.')]").exists());

        verify(paymentService, never()).pay(any());
    }

    @Test
    @DisplayName("TC-PAY-CTL-004: orderId가 없으면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void pay_withoutOrderId_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(authenticatedPaymentRequest("""
                        {
                          "paymentKey": "pay_key_001",
                          "paymentMethod": "CARD",
                          "amount": 150000.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'orderId')]").exists());

        verify(paymentService, never()).pay(any());
    }

    @Test
    @DisplayName("TC-PAY-CTL-005: paymentKey가 없거나 blank이면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void pay_withoutPaymentKeyOrBlankPaymentKey_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(authenticatedPaymentRequest("""
                        {
                          "orderId": 100,
                          "paymentMethod": "CARD",
                          "amount": 150000.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'paymentKey')]").exists());

        mockMvc.perform(authenticatedPaymentRequest("""
                        {
                          "orderId": 100,
                          "paymentKey": "   ",
                          "paymentMethod": "CARD",
                          "amount": 150000.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'paymentKey')]").exists());

        verify(paymentService, never()).pay(any());
    }

    @Test
    @DisplayName("TC-PAY-CTL-006: amount가 없으면 400 VALIDATION_ERROR를 반환하고 서비스를 호출하지 않는다")
    void pay_withoutAmount_returns400AndDoesNotCallService() throws Exception {
        mockMvc.perform(authenticatedPaymentRequest("""
                        {
                          "orderId": 100,
                          "paymentKey": "pay_key_001",
                          "paymentMethod": "CARD"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')]").exists());

        verify(paymentService, never()).pay(any());
    }

    @Test
    @DisplayName("TC-PAY-CTL-007: PAYMENT_AMOUNT_MISMATCH는 400으로 반환된다")
    void pay_whenServiceThrowsPaymentAmountMismatch_returns400() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class))).willThrow(new PaymentAmountMismatchException());

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-PAY-CTL-008: PAYMENT_FAILED는 400으로 반환된다")
    void pay_whenServiceThrowsPaymentFailed_returns400() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class))).willThrow(new PaymentFailedException());

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-PAY-CTL-012: cause가 있는 PAYMENT_FAILED도 400으로 반환된다")
    void pay_whenServiceThrowsPaymentFailedWithCause_returns400() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class)))
                .willThrow(new PaymentFailedException(new RuntimeException("toss down")));

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-PAY-CTL-009: ORDER_CANNOT_PAY는 409로 반환된다")
    void pay_whenServiceThrowsOrderCannotPay_returns409() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class))).willThrow(new OrderCannotPayException());

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_CANNOT_PAY"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-PAY-CTL-010: ORDER_NOT_FOUND는 404로 반환된다")
    void pay_whenServiceThrowsOrderNotFound_returns404() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class))).willThrow(new OrderNotFoundException());

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("TC-PAY-CTL-011: ORDER_FORBIDDEN은 403으로 반환된다")
    void pay_whenServiceThrowsOrderForbidden_returns403() throws Exception {
        given(paymentService.pay(any(PaymentCommand.class))).willThrow(new OrderForbiddenException());

        mockMvc.perform(authenticatedPaymentRequest(validRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ORDER_FORBIDDEN"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private RequestBuilder authenticatedPaymentRequest(String body) {
        return post("/api/v1/payments")
                .with(authentication(authenticatedUser()))
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String validRequestBody() {
        return """
                {
                  "orderId": 100,
                  "paymentKey": "pay_key_001",
                  "paymentMethod": "CARD",
                  "amount": 150000.00
                }
                """;
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
            return Clock.fixed(Instant.parse("2026-06-27T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
