package com.plateforme.shared.config;

import com.plateforme.shared.cache.HttpCacheInterceptor;
import com.plateforme.shared.ratelimit.RateLimitProperties;
import com.plateforme.shared.ratelimit.RateLimitingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitingInterceptor(rateLimitProperties))
                .addPathPatterns(
                        "/api/auth/login",
                        "/api/auth/signup",
                        "/api/auth/refresh",
                        "/api/marketplace/**"
                );

        registry.addInterceptor(new HttpCacheInterceptor())
                .addPathPatterns("/api/marketplace/**");
    }
}
