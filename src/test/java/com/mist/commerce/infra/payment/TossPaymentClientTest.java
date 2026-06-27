package com.mist.commerce.infra.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mist.commerce.domain.payment.exception.PaymentExceptionMessage;
import com.mist.commerce.domain.payment.exception.PaymentFailedException;
import com.mist.commerce.domain.payment.gateway.PaymentApproval;
import com.mist.commerce.domain.payment.gateway.PaymentApprovalCommand;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class TossPaymentClientTest {

    private static final String BASE_URL = "https://api.tosspayments.test";
    private static final String SECRET_KEY = "test_sk_123";
    private static final String CONFIRM_URL = BASE_URL + "/v1/payments/confirm";
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("TC-PAY-GW-001: Toss 승인 성공 응답을 PaymentApproval로 매핑한다")
    void approve_whenTossConfirmSucceeds_mapsPaymentApproval() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = client(builder);
        String responseBody = successResponseBody();

        server.expect(once(), requestTo(CONFIRM_URL))
                .andExpect(method(POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        PaymentApproval approval = client.approve(command("pay_key_001", "ORDER-001", "150000"));

        assertThat(approval.isApproved()).isTrue();
        assertThat(approval.getExternalTransactionId()).isEqualTo("pay_key_001");
        assertThat(approval.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 6, 27, 12, 3));
        assertThat(approval.getResponsePayload()).isEqualTo(responseBody);
        assertThat(approval.getRequestPayload()).contains("paymentKey", "pay_key_001");
        assertThat(approval.getRequestPayload()).contains("orderId", "ORDER-001");
        assertThat(approval.getRequestPayload()).contains("amount", "150000");
        assertNoPersistenceCollaborators();
        server.verify();
    }

    @Test
    @DisplayName("TC-PAY-GW-002: Toss confirm 요청 계약을 지킨다")
    void approve_sendsTossConfirmRequestContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = client(builder);

        server.expect(once(), requestTo(CONFIRM_URL))
                .andExpect(method(POST))
                .andExpect(header("Authorization", AUTHORIZATION))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentKey").value("pay_key_001"))
                .andExpect(jsonPath("$.orderId").value("ORDER-001"))
                .andExpect(jsonPath("$.amount").value(150000))
                .andRespond(withSuccess(successResponseBody(), MediaType.APPLICATION_JSON));

        PaymentApproval approval = client.approve(command("pay_key_001", "ORDER-001", "150000"));

        assertThat(approval.getRequestPayload()).contains("paymentKey", "pay_key_001");
        assertThat(approval.getRequestPayload()).contains("orderId", "ORDER-001");
        assertThat(approval.getRequestPayload()).contains("amount", "150000");
        assertNoPersistenceCollaborators();
        server.verify();
    }

    @Test
    @DisplayName("TC-PAY-GW-003: Toss 4xx 승인 실패를 PaymentFailedException으로 변환한다")
    void approve_whenTossReturns4xx_throwsPaymentFailedException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossPaymentClient client = client(builder);
        String failureBody = """
                {"code":"REJECT_CARD_COMPANY","message":"Card rejected"}
                """;

        server.expect(once(), requestTo(CONFIRM_URL))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(failureBody));

        assertThatThrownBy(() -> client.approve(command("pay_key_rejected", "ORDER-REJECTED", "150000")))
                .isExactlyInstanceOf(PaymentFailedException.class)
                .hasMessage(PaymentExceptionMessage.PAYMENT_FAILED.getMessage())
                .extracting("code", "httpStatus")
                .containsExactly("PAYMENT_FAILED", HttpStatus.BAD_REQUEST);
        assertNoPersistenceCollaborators();
        server.verify();
    }

    @Test
    @DisplayName("TC-PAY-GW-004: Toss 5xx 또는 네트워크 오류를 PaymentFailedException으로 변환한다")
    void approve_whenTossReturns5xxOrNetworkFails_throwsPaymentFailedExceptionWithCause() {
        RestClient.Builder serverErrorBuilder = RestClient.builder();
        MockRestServiceServer serverError = MockRestServiceServer.bindTo(serverErrorBuilder).build();
        TossPaymentClient serverErrorClient = client(serverErrorBuilder);
        serverError.expect(once(), requestTo(CONFIRM_URL))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> serverErrorClient.approve(command("pay_key_error", "ORDER-ERROR", "150000")))
                .isExactlyInstanceOf(PaymentFailedException.class)
                .hasMessage(PaymentExceptionMessage.PAYMENT_FAILED.getMessage())
                .hasCauseInstanceOf(Exception.class)
                .extracting("code", "httpStatus")
                .containsExactly("PAYMENT_FAILED", HttpStatus.BAD_REQUEST);
        serverError.verify();

        RestClient.Builder networkErrorBuilder = RestClient.builder();
        MockRestServiceServer networkError = MockRestServiceServer.bindTo(networkErrorBuilder).build();
        TossPaymentClient networkErrorClient = client(networkErrorBuilder);
        networkError.expect(once(), requestTo(CONFIRM_URL))
                .andExpect(method(POST))
                .andRespond(withException(new IOException("conn")));

        assertThatThrownBy(() -> networkErrorClient.approve(command("pay_key_error", "ORDER-ERROR", "150000")))
                .isExactlyInstanceOf(PaymentFailedException.class)
                .hasMessage(PaymentExceptionMessage.PAYMENT_FAILED.getMessage())
                .hasRootCauseInstanceOf(IOException.class)
                .extracting("code", "httpStatus")
                .containsExactly("PAYMENT_FAILED", HttpStatus.BAD_REQUEST);
        assertNoPersistenceCollaborators();
        networkError.verify();
    }

    private TossPaymentClient client(RestClient.Builder builder) {
        return new TossPaymentClient(builder, new TossPaymentProperties(BASE_URL, SECRET_KEY));
    }

    private PaymentApprovalCommand command(String paymentKey, String orderId, String amount) {
        return new PaymentApprovalCommand(paymentKey, orderId, new BigDecimal(amount));
    }

    private String successResponseBody() {
        return """
                {"paymentKey":"pay_key_001","orderId":"ORDER-001","status":"DONE","approvedAt":"2026-06-27T12:03:00+09:00"}
                """;
    }

    private void assertNoPersistenceCollaborators() {
        assertThat(Arrays.stream(TossPaymentClient.class.getDeclaredFields()).map(Field::getType))
                .noneMatch(Repository.class::isAssignableFrom)
                .noneMatch(EntityManager.class::isAssignableFrom)
                .noneMatch(type -> type.getPackageName().contains(".repository"));
    }
}
