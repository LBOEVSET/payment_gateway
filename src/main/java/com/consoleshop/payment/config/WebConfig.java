package com.consoleshop.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Request logging filter — logs method, URI, and client IP.
     * Detailed body logging is off to avoid leaking payment data.
     */
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        filter.setMaxPayloadLength(0);
        return filter;
    }
}
