package com.mist.commerce.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;


// DefaultOAuth2UserService 순환참조 문제로 분리 - 빈 등록 Configuration 클래스
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }
}
