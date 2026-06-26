package com.example.VideoService.Config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures all Feign (inter-service) calls to include the X-Internal-Key header
 * so that requests from VideoService → AuthService pass the AuthService security filter.
 */
@Configuration
public class FeignConfig {

    @Value("${app.internal-key}")
    private String internalKey;

    @Bean
    public RequestInterceptor internalKeyInterceptor() {
        return requestTemplate -> requestTemplate.header("X-Internal-Key", internalKey);
    }
}
