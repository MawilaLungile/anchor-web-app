package com.anchor.web;

import org.springframework.stereotype.Component;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.UserService;

/** Loads the full account for the signed-in principal. */
@Component
public class CurrentUser {

    private final UserService users;

    public CurrentUser(UserService users) {
        this.users = users;
    }

    public AppUser of(AnchorPrincipal principal) {
        return users.require(principal.getId());
    }
}
