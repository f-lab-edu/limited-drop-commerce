package com.mist.commerce.infra.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mist.commerce.domain.payment.exception.PaymentFailedException;
import com.mist.commerce.domain.payment.gateway.PaymentApproval;
import com.mist.commerce.domain.payment.gateway.PaymentApprovalCommand;
import com.mist.commerce.domain.payment.gateway.PaymentGateway;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TossPaymentClient implements PaymentGateway {

    private static final String CONFIRM_URI = "/v1/payments/confirm";

    private final RestClient restClient;
    private final String authorization;
    private final ObjectMapper objectMapper;

    public TossPaymentClient(RestClient.Builder builder, TossPaymentProperties props) {
        this.restClient = builder.baseUrl(props.baseUrl()).build();
        this.authorization = "Basic " + Base64.getEncoder()
                .encodeToString((props.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PaymentApproval approve(PaymentApprovalCommand command) {
        try {
            String requestPayload = createRequestPayload(command);
            String responsePayload = restClient.post()
                    .uri(CONFIRM_URI)
                    .header("Authorization", authorization)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            return toPaymentApproval(requestPayload, responsePayload);
        } catch (RestClientException | JsonProcessingException e) {
            throw new PaymentFailedException(e);
        }
    }

    private String createRequestPayload(PaymentApprovalCommand command) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "paymentKey", command.paymentKey(),
                "orderId", command.orderId(),
                "amount", command.amount()));
    }

    private PaymentApproval toPaymentApproval(String requestPayload, String responsePayload) throws JsonProcessingException {
        JsonNode response = objectMapper.readTree(responsePayload);
        String paymentKey = response.get("paymentKey").asText();
        OffsetDateTime approvedAt = OffsetDateTime.parse(response.get("approvedAt").asText());

        return new PaymentApproval(
                true,
                paymentKey,
                approvedAt.toLocalDateTime(),
                requestPayload,
                responsePayload);
    }
}
