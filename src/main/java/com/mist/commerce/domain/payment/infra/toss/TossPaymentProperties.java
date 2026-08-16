package com.mist.commerce.domain.payment.infra.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossPaymentProperties(
        String baseUrl,
        String secretKey
) {
}
