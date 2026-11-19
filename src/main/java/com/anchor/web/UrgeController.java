package com.anchor.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.FocusService;
import com.anchor.service.ShieldService;

/** The urge timer: a 60-second guided breathing pause, a short reflection, then support. */
@Controller
public class UrgeController {

    public record Idea(String icon, String title, String detail) {
    }

    public static final List<Idea> IDEAS = List.of(
            new Idea("directions_walk", "Move your body", "A ten-minute walk resets your brain chemistry."),
            new Idea("water_drop", "Drink a glass of water", "Simple, physical, and it breaks the loop."),
            new Idea("call", "Reach out", "Message someone you trust. You don't have to explain."),
            new Idea("music_note", "Change the room", "Different place, different music, lights on."));

    private final CurrentUser current;
    private final ShieldService shield;
    private final FocusService focus;

    public UrgeController(CurrentUser current, ShieldService shield, FocusService focus) {
        this.current = current;
        this.shield = shield;
        this.focus = focus;
    }

    @GetMapping("/urge")
    public String page(@RequestParam(defaultValue = "manual") String source, Model model) {
        boolean session = "session".equals(source);
        model.addAttribute("source", session ? "session" : "manual");
        model.addAttribute("headline", session ? "Pause before you quit." : "This feeling will pass.");
        model.addAttribute("subline", session
                ? "The pull to stop is strongest right before focus deepens. Breathe through it, then decide."
                : "Urges rise, peak and fall like a wave. Ride this one out with me for sixty seconds.");
        model.addAttribute("triggers", ShieldService.TRIGGER_ICONS);
        return "urge";
    }

    @PostMapping("/urge")
    public String reflect(@AuthenticationPrincipal AnchorPrincipal principal,
                          @RequestParam(defaultValue = "Other") String trigger,
                          @RequestParam(defaultValue = "manual") String source,
                          @RequestParam boolean resisted,
                          RedirectAttributes flash) {
        AppUser user = current.of(principal);
        shield.logUrge(user, trigger, source, resisted);
        flash.addFlashAttribute("resisted", resisted);
        flash.addFlashAttribute("trigger", trigger);
        flash.addFlashAttribute("fromSession", "session".equals(source) && focus.active(user).isPresent());
        flash.addFlashAttribute("offerReset", !resisted && !"session".equals(source));
        return "redirect:/urge/done";
    }

    @GetMapping("/urge/done")
    public String done(Model model) {
        if (!model.containsAttribute("resisted")) {
            return "redirect:/";
        }
        model.addAttribute("ideas", IDEAS);
        return "urge-done";
    }
}
