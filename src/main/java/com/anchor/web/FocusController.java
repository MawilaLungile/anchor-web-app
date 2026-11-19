package com.anchor.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.domain.FocusSession;
import com.anchor.domain.SiteList;
import com.anchor.domain.TaskItem;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.Analytics;
import com.anchor.service.FocusService;
import com.anchor.service.ShieldService;
import com.anchor.service.TaskService;
import com.anchor.service.UserService;
import com.anchor.util.Fmt;
import com.anchor.util.UserClock;

@Controller
public class FocusController {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    public static final List<Integer> DURATIONS = List.of(15, 25, 50, 90);

    /** A finished session as shown in the history list. */
    public record SessionView(String intention, String detail, boolean kept, String reason) {
    }

    private final CurrentUser current;
    private final UserService users;
    private final FocusService focus;
    private final TaskService tasks;
    private final ShieldService shield;

    public FocusController(CurrentUser current, UserService users, FocusService focus, TaskService tasks, ShieldService shield) {
        this.current = current;
        this.users = users;
        this.focus = focus;
        this.tasks = tasks;
        this.shield = shield;
    }

    @GetMapping("/focus")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal,
                       @RequestParam(required = false) Long taskId, Model model) {
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);
        LocalDate today = clock.today();

        FocusSession active = focus.active(user).orElse(null);
        model.addAttribute("activeSession", active);
        if (active != null) {
            model.addAttribute("startMillis", active.getStartedAt().toEpochMilli());
            model.addAttribute("endMillis", active.getPlannedEnd().toEpochMilli());
            model.addAttribute("endsAt", clock.local(active.getPlannedEnd()).format(CLOCK));
        }

        // Setup
        List<TaskItem> open = tasks.forDay(user, today).stream()
                .filter(t -> !t.isDone())
                .sorted(Comparator.comparing(TaskItem::isOneThing).reversed().thenComparing(TaskItem::isDeep, Comparator.reverseOrder()))
                .limit(6)
                .toList();
        TaskItem preselected = taskId == null ? null : open.stream().filter(t -> t.getId().equals(taskId)).findFirst().orElse(null);
        model.addAttribute("openTasks", open);
        model.addAttribute("selectedTaskId", preselected == null ? null : preselected.getId());
        model.addAttribute("intention", preselected == null ? "" : preselected.getTitle());
        int defaultMinutes = preselected == null ? user.getDefaultFocusMinutes() : closest(preselected.getEstimateMinutes());
        model.addAttribute("durations", DURATIONS);
        model.addAttribute("defaultMinutes", closest(defaultMinutes));
        model.addAttribute("blockDefault", user.isBlockDuringFocus());
        model.addAttribute("focusSiteCount", shield.count(user, SiteList.FOCUS));

        // Stats
        List<FocusSession> month = focus.since(user, clock.startOf(today.minusDays(30)));
        List<FocusSession> finished = month.stream().filter(FocusSession::isFinished).toList();
        model.addAttribute("todayText", Fmt.minutes(finished.stream()
                .filter(s -> clock.date(s.getStartedAt()).equals(today)).mapToInt(FocusSession::getFocusedMinutes).sum()));
        model.addAttribute("keptRate", finished.isEmpty() ? "–" : Fmt.percent(Analytics.keptRate(finished)));
        model.addAttribute("streak", Fmt.days(Analytics.focusDayStreak(finished, s -> clock.date(s.getStartedAt()), today)));
        model.addAttribute("recent", finished.stream().limit(8).map(s -> new SessionView(
                s.getIntention(),
                clock.local(s.getStartedAt()).format(WHEN) + " · " + (s.isKept()
                        ? s.getPlannedMinutes() + " min"
                        : s.getFocusedMinutes() + " of " + s.getPlannedMinutes() + " min"),
                s.isKept(),
                s.getBreakReason())).toList());

        return "focus";
    }

    @PostMapping("/focus/start")
    public String start(@AuthenticationPrincipal AnchorPrincipal principal,
                        @RequestParam(defaultValue = "") String intention,
                        @RequestParam(defaultValue = "50") int minutes,
                        @RequestParam(required = false) Long taskId,
                        @RequestParam(defaultValue = "false") boolean block,
                        RedirectAttributes flash) {
        if (intention.isBlank() && taskId == null) {
            flash.addFlashAttribute("toast", "Write what you're committing to first");
            return "redirect:/focus";
        }
        AppUser user = current.of(principal);
        String text = intention;
        if (text.isBlank() && taskId != null) {
            text = tasks.get(user, taskId).getTitle();
        }
        focus.start(user, text, minutes, taskId, block);
        return "redirect:/focus";
    }

    @PostMapping("/focus/extend")
    public String extend(@AuthenticationPrincipal AnchorPrincipal principal) {
        focus.extend(current.of(principal), 10);
        return "redirect:/focus";
    }

    @PostMapping("/focus/complete")
    public String complete(@AuthenticationPrincipal AnchorPrincipal principal, RedirectAttributes flash) {
        focus.complete(current.of(principal));
        flash.addFlashAttribute("toast", "Commitment kept. Take a proper break.");
        return "redirect:/focus";
    }

    @PostMapping("/focus/end")
    public String end(@AuthenticationPrincipal AnchorPrincipal principal,
                      @RequestParam(defaultValue = "") String reason, RedirectAttributes flash) {
        focus.breakEarly(current.of(principal), reason);
        flash.addFlashAttribute("toast", "Session ended. Your reason is saved for Insights.");
        return "redirect:/focus";
    }

    private static int closest(int minutes) {
        return DURATIONS.stream().min(Comparator.comparingInt(d -> Math.abs(d - minutes))).orElse(50);
    }
}
