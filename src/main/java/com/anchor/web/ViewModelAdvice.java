package com.anchor.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.anchor.domain.AppUser;
import com.anchor.repo.AppUserRepository;
import com.anchor.security.AnchorPrincipal;
import com.anchor.util.Fmt;

/** Adds the signed-in user's display details to every page. */
@ControllerAdvice
public class ViewModelAdvice {

    /** What every page needs to know about the user. */
    public record Me(String name, String initials, String email, String theme) {
    }

    private final AppUserRepository users;

    public ViewModelAdvice(AppUserRepository users) {
        this.users = users;
    }

    @ModelAttribute("me")
    public Me me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AnchorPrincipal principal)) {
            return null;
        }
        AppUser user = users.findById(principal.getId()).orElse(null);
        if (user == null) {
            return null;
        }
        return new Me(user.getDisplayName(), Fmt.initials(user.getDisplayName()), user.getEmail(), user.getTheme());
    }
}
