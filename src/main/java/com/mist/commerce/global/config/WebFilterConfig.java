package com.mist.commerce.global.config;

import com.mist.commerce.global.web.CachedBodyFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilter() {
        FilterRegistrationBean<CachedBodyFilter> registrationBean =
                new FilterRegistrationBean<>();

        registrationBean.setFilter(new CachedBodyFilter());
        registrationBean.addUrlPatterns("/api/v1/reservations/*", "/api/v1/payments/*");
        registrationBean.setOrder(1);

        return registrationBean;
    }
}
