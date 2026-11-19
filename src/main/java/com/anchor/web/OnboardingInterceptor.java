package com.anchor.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.anchor.repo.AppUserRepository;
import com.anchor.security.AnchorPrincipal;

/** Sends signed-in users who haven't finished the welcome tour to /welcome first. */
@Component
public class OnboardingInterceptor implements HandlerInterceptor {

    private final AppUserRepository users;

    public OnboardingInterceptor(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AnchorPrincipal principal)) {
            return true;
        }
        boolean onboarded = users.findById(principal.getId()).map(u -> u.isOnboarded()).orElse(true);
        if (!onboarded) {
            response.sendRedirect(request.getContextPath() + "/welcome");
            return false;
        }
        return true;
    }
}
