package com.anchor.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.anchor.repo.AppUserRepository;

@Service
public class AnchorUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AnchorUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return users.findByEmail(normalized)
                .map(AnchorPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + normalized));
    }
}
