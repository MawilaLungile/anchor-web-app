package com.anchor.web;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.domain.EnergyEntry;
import com.anchor.domain.FocusSession;
import com.anchor.domain.TaskItem;
import com.anchor.domain.TaskSlot;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.Analytics;
import com.anchor.service.EnergyService;
import com.anchor.service.FocusService;
import com.anchor.service.ReviewService;
import com.anchor.service.TaskService;
import com.anchor.service.UserService;
import com.anchor.util.Fmt;
import com.anchor.util.UserClock;

@Controller
public class TodayController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    private final CurrentUser current;
    private final UserService users;
    private final TaskService tasks;
    private final FocusService focus;
    private final EnergyService energy;
    private final ReviewService reviews;

    public TodayController(CurrentUser current, UserService users, TaskService tasks, FocusService focus,
                           EnergyService energy, ReviewService reviews) {
        this.current = current;
        this.users = users;
        this.tasks = tasks;
        this.focus = focus;
        this.energy = energy;
        this.reviews = reviews;
    }

    @GetMapping("/")
    public String today(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);
        ZonedDateTime now = clock.nowLocal();
        LocalDate today = clock.today();

        model.addAttribute("dateText", now.format(DATE));
        model.addAttribute("greeting", Views.greeting(now.getHour()) + ", " + user.getDisplayName());

        // Plan
        List<TaskItem> all = tasks.forDay(user, today);
        TaskItem oneThing = all.stream().filter(TaskItem::isOneThing).findFirst().orElse(null);
        List<TaskItem> nice = all.stream().filter(t -> t.getSlot() == TaskSlot.NICE_TO_HAVE).sorted(TaskService.openFirst()).toList();
        List<TaskItem> others = all.stream().filter(t -> t.getSlot() == TaskSlot.OTHER).sorted(TaskService.openFirst()).toList();
        long done = all.stream().filter(TaskItem::isDone).count();

        model.addAttribute("oneThing", oneThing);
        model.addAttribute("niceToHaves", nice);
        model.addAttribute("others", others);
        model.addAttribute("canAddNice", nice.size() < TaskService.MAX_NICE_TO_HAVES);
        model.addAttribute("dayRing", new Views.Ring(all.isEmpty() ? 0 : done / (double) all.size()));
        model.addAttribute("progressText", all.isEmpty() ? "Nothing planned yet" : done + " of " + all.size() + " done");
        model.addAttribute("overdueCount", tasks.overdue(user).size());

        // Focus today
        List<FocusSession> sessions = focus.since(user, clock.startOf(today));
        model.addAttribute("focusToday", Fmt.minutes(sessions.stream().filter(FocusSession::isFinished)
                .mapToInt(FocusSession::getFocusedMinutes).sum()));
        model.addAttribute("keptToday", sessions.stream().filter(FocusSession::isKept).count());
        FocusSession active = focus.active(user).orElse(null);
        model.addAttribute("activeSession", active);
        if (active != null) {
            model.addAttribute("activeEndMillis", active.getPlannedEnd().toEpochMilli());
        }

        // Energy
        List<EnergyEntry> recent = energy.since(user, clock.startOf(today.minusDays(28)));
        EnergyEntry latest = recent.isEmpty() ? null : recent.get(0);
        if (latest != null && clock.date(latest.getRecordedAt()).equals(today)) {
            model.addAttribute("energyLevel", EnergyService.level(latest.getLevel()));
            model.addAttribute("energyWhen", "Checked in " + Fmt.relative(Duration.between(latest.getRecordedAt(), clock.now())));
        }
        Analytics.Window peak = Analytics.findWindow(recent, e -> clock.local(e.getRecordedAt()), true);
        model.addAttribute("peak", peak);
        if (peak != null) {
            int hour = now.getHour();
            model.addAttribute("peakAdvice", hour < peak.startHour()
                    ? "Protect this window for your One Thing."
                    : hour < peak.endHour()
                        ? "You're in your peak window right now. Ideal time for deep work."
                        : "Your peak has passed for today. Lean on light tasks and planning.");
        }

        // Evening shutdown
        model.addAttribute("showShutdown", now.getHour() >= 17);
        model.addAttribute("dayClosed", reviews.forDay(user, today).isPresent());

        return "today";
    }

    @PostMapping("/tasks/{id}/toggle")
    public String toggle(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id,
                         @RequestParam(defaultValue = "/") String back) {
        tasks.toggle(current.of(principal), id);
        return "redirect:" + safeBack(back);
    }

    @PostMapping("/tasks/carry-over")
    public String carryOver(@AuthenticationPrincipal AnchorPrincipal principal, RedirectAttributes flash) {
        int moved = tasks.carryOverToToday(current.of(principal));
        flash.addFlashAttribute("toast", moved == 1 ? "1 task moved to today" : moved + " tasks moved to today");
        return "redirect:/";
    }

    /** Only allow local redirects. */
    static String safeBack(String back) {
        return back != null && back.startsWith("/") && !back.startsWith("//") ? back : "/";
    }
}
