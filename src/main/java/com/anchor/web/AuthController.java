package com.anchor.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.UserService;

@Controller
public class AuthController {

    public static class RegisterForm {
        @NotBlank(message = "Tell us what to call you")
        @Size(max = 60)
        private String displayName;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254)
        private String email;

        @NotBlank(message = "Choose a password")
        @Size(min = 8, max = 100, message = "Use at least 8 characters")
        private String password;

        private String timeZone;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getTimeZone() { return timeZone; }
        public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    }

    private final UserService users;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(UserService users) {
        this.users = users;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result,
                           HttpServletRequest request, HttpServletResponse response) {
        if (!result.hasFieldErrors("email") && users.emailTaken(form.getEmail())) {
            result.rejectValue("email", "taken", "An account with this email already exists");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }

        AppUser user = users.register(form.getEmail(), form.getPassword(), form.getDisplayName(), form.getTimeZone());

        // Sign the new user straight in.
        AnchorPrincipal principal = new AnchorPrincipal(user);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return "redirect:/welcome";
    }
}
