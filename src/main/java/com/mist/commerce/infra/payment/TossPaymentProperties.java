package com.mist.commerce.infra.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossPaymentProperties(
        String baseUrl,
        String secretKey
) {
}
