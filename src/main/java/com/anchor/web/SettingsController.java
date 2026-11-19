package com.anchor.web;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.DataService;
import com.anchor.service.ShieldService;
import com.anchor.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Controller
public class SettingsController {

    private static final List<String> ZONES = new TreeSet<>(ZoneId.getAvailableZoneIds()).stream()
            .filter(z -> z.contains("/") && !z.startsWith("Etc/") && !z.startsWith("SystemV/"))
            .toList();

    private final CurrentUser current;
    private final UserService users;
    private final DataService data;
    private final ShieldService shield;
    private final ObjectMapper mapper;

    public SettingsController(CurrentUser current, UserService users, DataService data, ShieldService shield, ObjectMapper mapper) {
        this.current = current;
        this.users = users;
        this.data = data;
        this.shield = shield;
        this.mapper = mapper;
    }

    @GetMapping("/settings")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        AppUser user = current.of(principal);
        model.addAttribute("user", user);
        model.addAttribute("zones", ZONES);
        model.addAttribute("themes", List.of(
                new Views.Option("system", "System", "contrast", "system".equals(user.getTheme())),
                new Views.Option("light", "Light", "light_mode", "light".equals(user.getTheme())),
                new Views.Option("dark", "Dark", "dark_mode", "dark".equals(user.getTheme()))));
        model.addAttribute("focusLengths", FocusController.DURATIONS.stream()
                .map(m -> new Views.Option(String.valueOf(m), m + " min", null, m == user.getDefaultFocusMinutes())).toList());
        model.addAttribute("coolingOptions", List.of(
                new Views.Option("5", "5 min", null, user.getCoolingOffMinutes() == 5),
                new Views.Option("30", "30 min", null, user.getCoolingOffMinutes() == 30),
                new Views.Option("120", "2 hours", null, user.getCoolingOffMinutes() == 120),
                new Views.Option("1440", "24 hours", null, user.getCoolingOffMinutes() == 1440)));
        model.addAttribute("memberSince", user.getCreatedAt().atZone(ZoneId.of(user.getTimeZone()))
                .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)));
        return "settings";
    }

    @PostMapping("/settings/profile")
    public String profile(@AuthenticationPrincipal AnchorPrincipal principal,
                          @RequestParam String displayName, @RequestParam String timeZone, RedirectAttributes flash) {
        if (displayName.isBlank() || displayName.length() > 60) {
            flash.addFlashAttribute("toast", "Your name must be between 1 and 60 characters");
            return "redirect:/settings";
        }
        users.updateProfile(current.of(principal), displayName, timeZone);
        flash.addFlashAttribute("toast", "Profile saved");
        return "redirect:/settings";
    }

    @PostMapping("/settings/preferences")
    public String preferences(@AuthenticationPrincipal AnchorPrincipal principal,
                              @RequestParam(defaultValue = "system") String theme,
                              @RequestParam(defaultValue = "50") int focusMinutes,
                              @RequestParam(defaultValue = "false") boolean blockDuringFocus,
                              RedirectAttributes flash) {
        users.updatePreferences(current.of(principal), theme, focusMinutes, blockDuringFocus);
        flash.addFlashAttribute("toast", "Preferences saved");
        return "redirect:/settings";
    }

    @PostMapping("/settings/cooling-off")
    public String coolingOff(@AuthenticationPrincipal AnchorPrincipal principal, @RequestParam int minutes, RedirectAttributes flash) {
        AppUser user = current.of(principal);
        boolean ok = users.updateCoolingOff(user, minutes, shield.hasPendingRemovals(user));
        flash.addFlashAttribute("toast", ok ? "Cooling-off period saved"
                : "You can't shorten the cooling-off while a removal is pending. Cancel it first.");
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String password(@AuthenticationPrincipal AnchorPrincipal principal,
                           @RequestParam String current, @RequestParam String next, RedirectAttributes flash) {
        if (next.length() < 8 || next.length() > 100) {
            flash.addFlashAttribute("toast", "Your new password needs at least 8 characters");
            return "redirect:/settings";
        }
        boolean ok = users.changePassword(this.current.of(principal), current, next);
        flash.addFlashAttribute("toast", ok ? "Password changed" : "Your current password is incorrect");
        return "redirect:/settings";
    }

    @GetMapping("/settings/export.json")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AnchorPrincipal principal) throws Exception {
        byte[] json = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsBytes(data.export(current.of(principal)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"anchor-export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping("/settings/sample")
    public String sample(@AuthenticationPrincipal AnchorPrincipal principal, RedirectAttributes flash) {
        data.loadSample(current.of(principal));
        flash.addFlashAttribute("toast", "Sample data loaded. Explore Today, Focus and Insights.");
        return "redirect:/";
    }

    @PostMapping("/settings/erase")
    public String erase(@AuthenticationPrincipal AnchorPrincipal principal, RedirectAttributes flash) {
        data.erase(current.of(principal), true);
        flash.addFlashAttribute("toast", "Your history was erased. Block lists were kept.");
        return "redirect:/";
    }

    @PostMapping("/settings/delete-account")
    public String deleteAccount(@AuthenticationPrincipal AnchorPrincipal principal, @RequestParam String password,
                                HttpServletRequest request, HttpServletResponse response, RedirectAttributes flash) {
        AppUser user = current.of(principal);
        if (!users.passwordMatches(user, password)) {
            flash.addFlashAttribute("toast", "Password incorrect. Your account was not deleted.");
            return "redirect:/settings";
        }
        users.delete(user);
        new SecurityContextLogoutHandler().logout(request, response, null);
        return "redirect:/login?deleted";
    }
}
