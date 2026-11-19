package com.anchor.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final OnboardingInterceptor onboarding;

    public WebConfig(OnboardingInterceptor onboarding) {
        this.onboarding = onboarding;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onboarding)
                .addPathPatterns("/**")
                .excludePathPatterns("/welcome", "/login", "/register", "/logout", "/error", "/actuator/**",
                        "/css/**", "/js/**", "/fonts/**", "/img/**", "/favicon.svg");
    }
}
