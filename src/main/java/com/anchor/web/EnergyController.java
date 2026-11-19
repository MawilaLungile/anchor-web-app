package com.anchor.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.EnergyService;
import com.anchor.service.UserService;
import com.anchor.util.UserClock;

@Controller
public class EnergyController {

    private final CurrentUser current;
    private final UserService users;
    private final EnergyService energy;

    public EnergyController(CurrentUser current, UserService users, EnergyService energy) {
        this.current = current;
        this.users = users;
        this.energy = energy;
    }

    @GetMapping("/energy")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);
        int count = energy.since(user, clock.startOf(clock.today())).size();
        model.addAttribute("levels", EnergyService.LEVELS);
        model.addAttribute("time", String.format("%02d:%02d", clock.nowLocal().getHour(), clock.nowLocal().getMinute()));
        model.addAttribute("todayText", switch (count) {
            case 0 -> "Your first check-in today. Two seconds, that's all.";
            case 1 -> "1 check-in so far today. A few a day reveal your peak hours.";
            default -> count + " check-ins so far today. Your energy map is getting sharper.";
        });
        return "energy";
    }

    @PostMapping("/energy")
    public String save(@AuthenticationPrincipal AnchorPrincipal principal, @RequestParam int level, RedirectAttributes flash) {
        energy.record(current.of(principal), level);
        flash.addFlashAttribute("toast", "Energy logged: " + EnergyService.level(level).name());
        return "redirect:/";
    }
}
