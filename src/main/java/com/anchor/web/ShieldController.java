package com.anchor.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.domain.SiteList;
import com.anchor.domain.UrgeEntry;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.Analytics;
import com.anchor.service.BlockListService;
import com.anchor.service.ShieldService;
import com.anchor.service.UserService;
import com.anchor.util.Fmt;
import com.anchor.util.UserClock;

@Controller
public class ShieldController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CurrentUser current;
    private final UserService users;
    private final ShieldService shield;
    private final BlockListService lists;

    public ShieldController(CurrentUser current, UserService users, ShieldService shield, BlockListService lists) {
        this.current = current;
        this.users = users;
        this.shield = shield;
        this.lists = lists;
    }

    @GetMapping("/shield")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);

        long days = shield.streakDays(user);
        int next = Analytics.nextMilestone(days);
        model.addAttribute("streakDays", days);
        model.addAttribute("streakUnit", days == 1 ? "day clean" : "days clean");
        model.addAttribute("streakRing", new Views.Ring(days / (double) next));
        model.addAttribute("milestoneText", Fmt.days(next - days) + " to your " + next + "-day milestone");
        model.addAttribute("longestText", "Longest streak: " + Fmt.days(shield.longestStreak(user)));

        List<UrgeEntry> week = shield.urgesSince(user, clock.startOf(clock.today().minusDays(6)));
        model.addAttribute("urgesText", week.isEmpty()
                ? "No urges logged this week"
                : week.stream().filter(UrgeEntry::isResisted).count() + " of " + week.size() + " urges ridden out this week");

        model.addAttribute("adultCount", Fmt.number(lists.adultDomains().size()));
        Instant updated = lists.updatedAt();
        model.addAttribute("listUpdated", updated == null ? "Bundled list"
                : "Updated " + DAY.format(updated.atZone(ZoneId.of("UTC"))));
        model.addAttribute("alwaysCount", shield.count(user, SiteList.ALWAYS));
        model.addAttribute("focusCount", shield.count(user, SiteList.FOCUS));
        model.addAttribute("coolingText", Fmt.minutes(user.getCoolingOffMinutes()));
        return "shield/index";
    }

    @PostMapping("/shield/reset-streak")
    public String resetStreak(@AuthenticationPrincipal AnchorPrincipal principal, RedirectAttributes flash) {
        shield.resetStreak(current.of(principal));
        flash.addFlashAttribute("toast", "Fresh start. Day one begins now. Be kind to yourself.");
        return "redirect:/shield";
    }

    @PostMapping("/shield/update-list")
    public String updateList(RedirectAttributes flash) {
        Instant updated = lists.updatedAt();
        if (updated != null && Duration.between(updated, Instant.now()).toHours() < 12) {
            flash.addFlashAttribute("toast", "The list was updated recently. Try again later.");
            return "redirect:/shield";
        }
        try {
            int count = lists.update();
            flash.addFlashAttribute("toast", "Blocklist updated: " + Fmt.number(count) + " domains");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            flash.addFlashAttribute("toast", "Couldn't download the latest list. The current one stays active.");
        }
        return "redirect:/shield";
    }

    /** A ready-made hosts file with the user's lists (and optionally the adult list). */
    @GetMapping("/shield/hosts.txt")
    public ResponseEntity<StreamingResponseBody> hosts(@AuthenticationPrincipal AnchorPrincipal principal,
                                                       @RequestParam(defaultValue = "true") boolean adult,
                                                       @RequestParam(defaultValue = "false") boolean focus) {
        AppUser user = current.of(principal);
        StreamingResponseBody body = out -> shield.writeHostsFile(user, adult, focus, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"anchor-hosts.txt\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    // ------------------------------------------------------------------ Sites

    @GetMapping("/shield/sites")
    public String sites(@AuthenticationPrincipal AnchorPrincipal principal,
                        @RequestParam(defaultValue = "FOCUS") SiteList list, Model model) {
        AppUser user = current.of(principal);
        model.addAttribute("list", list);
        model.addAttribute("sites", shield.sites(user, list));
        model.addAttribute("presets", ShieldService.PRESETS);
        model.addAttribute("coolingText", Fmt.minutes(user.getCoolingOffMinutes()));
        return "shield/sites";
    }

    @PostMapping("/shield/sites")
    public String add(@AuthenticationPrincipal AnchorPrincipal principal,
                      @RequestParam String domain, @RequestParam SiteList list, RedirectAttributes flash) {
        ShieldService.AddResult result = shield.add(current.of(principal), domain, list);
        flash.addFlashAttribute("toast", switch (result) {
            case ADDED -> "Site added";
            case DUPLICATE -> "That site is already on this list";
            case INVALID -> "Enter a website like youtube.com or paste a full link";
            case SAFE_LISTED -> "Blocking this domain could break your device, so Anchor keeps it reachable";
        });
        return "redirect:/shield/sites?list=" + list;
    }

    @PostMapping("/shield/presets/{index}")
    public String preset(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable int index, RedirectAttributes flash) {
        int added = shield.addPreset(current.of(principal), index);
        flash.addFlashAttribute("toast", added == 0 ? "Those sites are already on your list" : added + " sites added");
        return "redirect:/shield/sites?list=FOCUS";
    }

    @PostMapping("/shield/sites/{id}/remove")
    public String remove(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id,
                         @RequestParam SiteList list, RedirectAttributes flash) {
        boolean removed = shield.requestRemoval(current.of(principal), id);
        flash.addFlashAttribute("toast", removed ? "Site removed" : "Cooling-off started. You can confirm when it ends.");
        return "redirect:/shield/sites?list=" + list;
    }

    @PostMapping("/shield/sites/{id}/confirm")
    public String confirm(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id, RedirectAttributes flash) {
        boolean removed = shield.confirmRemoval(current.of(principal), id);
        flash.addFlashAttribute("toast", removed ? "Site removed" : "The cooling-off window has passed. Start again if you still want to.");
        return "redirect:/shield/sites?list=ALWAYS";
    }

    @PostMapping("/shield/sites/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id, RedirectAttributes flash) {
        shield.cancelRemoval(current.of(principal), id);
        flash.addFlashAttribute("toast", "Good call. The site stays blocked.");
        return "redirect:/shield/sites?list=ALWAYS";
    }
}
