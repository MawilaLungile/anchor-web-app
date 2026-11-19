package com.anchor.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.DataService;
import com.anchor.service.UserService;

@Controller
public class WelcomeController {

    private final CurrentUser current;
    private final DataService data;
    private final UserService users;

    public WelcomeController(CurrentUser current, DataService data, UserService users) {
        this.current = current;
        this.data = data;
        this.users = users;
    }

    @GetMapping("/welcome")
    public String welcome(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        model.addAttribute("name", current.of(principal).getDisplayName());
        return "welcome";
    }

    @PostMapping("/welcome")
    public String finish(@AuthenticationPrincipal AnchorPrincipal principal,
                         @RequestParam(defaultValue = "fresh") String mode) {
        AppUser user = current.of(principal);
        data.ensureDefaultSites(user);
        if ("sample".equals(mode)) {
            data.loadSample(user);
        }
        users.markOnboarded(user);
        return "redirect:/";
    }
}
