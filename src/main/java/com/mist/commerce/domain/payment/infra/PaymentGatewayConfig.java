package com.mist.commerce.domain.payment.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    public RestClient.Builder tossRestClientBuilder() {
        return RestClient.builder();
    }
}
