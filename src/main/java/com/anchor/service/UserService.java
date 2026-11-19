package com.anchor.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.repo.AppUserRepository;
import com.anchor.util.UserClock;

@Service
public class UserService {

    public static final Set<String> THEMES = Set.of("system", "light", "dark");
    public static final Set<Integer> FOCUS_LENGTHS = Set.of(15, 25, 50, 90);
    public static final Set<Integer> COOLING_OPTIONS = Set.of(5, 30, 120, 1440);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UserService(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    public AppUser require(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User"));
    }

    public UserClock clockFor(AppUser user) {
        return new UserClock(clock, user.getTimeZone());
    }

    public boolean emailTaken(String email) {
        return users.existsByEmail(normalizeEmail(email));
    }

    @Transactional
    public AppUser register(String email, String password, String displayName, String timeZone) {
        AppUser user = new AppUser();
        user.setEmail(normalizeEmail(email));
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName.trim());
        user.setTimeZone(UserClock.parseZone(timeZone).getId());
        user.setTheme("system");
        user.setStreakStart(clock.instant());
        user.setCreatedAt(clock.instant());
        return users.save(user);
    }

    @Transactional
    public void updateProfile(AppUser user, String displayName, String timeZone) {
        user.setDisplayName(displayName.trim());
        user.setTimeZone(UserClock.parseZone(timeZone).getId());
        users.save(user);
    }

    @Transactional
    public void updatePreferences(AppUser user, String theme, int focusMinutes, boolean blockDuringFocus) {
        if (THEMES.contains(theme)) {
            user.setTheme(theme);
        }
        if (FOCUS_LENGTHS.contains(focusMinutes)) {
            user.setDefaultFocusMinutes(focusMinutes);
        }
        user.setBlockDuringFocus(blockDuringFocus);
        users.save(user);
    }

    /**
     * Changes the cooling-off period. It can always be made stricter, but making it shorter is
     * only allowed when no always-blocked removals are pending (otherwise it would defeat its purpose).
     */
    @Transactional
    public boolean updateCoolingOff(AppUser user, int minutes, boolean removalsPending) {
        if (!COOLING_OPTIONS.contains(minutes)) {
            return false;
        }
        if (minutes < user.getCoolingOffMinutes() && removalsPending) {
            return false;
        }
        user.setCoolingOffMinutes(minutes);
        users.save(user);
        return true;
    }

    @Transactional
    public boolean changePassword(AppUser user, String current, String next) {
        if (!encoder.matches(current, user.getPasswordHash())) {
            return false;
        }
        user.setPasswordHash(encoder.encode(next));
        users.save(user);
        return true;
    }

    public boolean passwordMatches(AppUser user, String password) {
        return encoder.matches(password, user.getPasswordHash());
    }

    @Transactional
    public void markOnboarded(AppUser user) {
        user.setOnboarded(true);
        users.save(user);
    }

    @Transactional
    public void save(AppUser user) {
        users.save(user);
    }

    @Transactional
    public void delete(AppUser user) {
        users.delete(user); // child rows are removed by ON DELETE CASCADE
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
