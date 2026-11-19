package com.anchor.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.domain.DailyReview;
import com.anchor.domain.FocusSession;
import com.anchor.domain.TaskItem;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.FocusService;
import com.anchor.service.ReviewService;
import com.anchor.service.TaskService;
import com.anchor.service.UserService;
import com.anchor.util.Fmt;
import com.anchor.util.UserClock;

/** The evening shutdown ritual. */
@Controller
public class ReviewController {

    private final CurrentUser current;
    private final UserService users;
    private final TaskService tasks;
    private final FocusService focus;
    private final ReviewService reviews;

    public ReviewController(CurrentUser current, UserService users, TaskService tasks, FocusService focus, ReviewService reviews) {
        this.current = current;
        this.users = users;
        this.tasks = tasks;
        this.focus = focus;
        this.reviews = reviews;
    }

    @GetMapping("/review")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal, Model model) {
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);
        LocalDate today = clock.today();

        List<TaskItem> all = tasks.forDay(user, today);
        List<TaskItem> done = all.stream().filter(TaskItem::isDone).toList();
        List<TaskItem> open = all.stream().filter(t -> !t.isDone()).toList();
        List<FocusSession> sessions = focus.since(user, clock.startOf(today)).stream().filter(FocusSession::isFinished).toList();

        int minutes = sessions.stream().mapToInt(FocusSession::getFocusedMinutes).sum();
        long kept = sessions.stream().filter(FocusSession::isKept).count();
        model.addAttribute("summary", sessions.isEmpty()
                ? done.size() + " tasks done today."
                : done.size() + " tasks done · " + Fmt.minutes(minutes) + " focused · " + kept + " of " + sessions.size() + " commitments kept");
        model.addAttribute("done", done);
        model.addAttribute("open", open);
        model.addAttribute("ratings", ReviewService.RATINGS);

        DailyReview existing = reviews.forDay(user, today).orElse(null);
        model.addAttribute("closed", existing != null);
        if (existing != null) {
            model.addAttribute("closedText", "You closed today with a \"" + ReviewService.RATINGS.get(existing.getRating() - 1)
                    + "\" rating. Rest well.");
        }
        return "review";
    }

    @PostMapping("/review")
    public String close(@AuthenticationPrincipal AnchorPrincipal principal,
                        @RequestParam(defaultValue = "4") int rating,
                        @RequestParam(required = false) String note,
                        @RequestParam(name = "move", required = false) List<Long> move,
                        RedirectAttributes flash) {
        AppUser user = current.of(principal);
        LocalDate today = users.clockFor(user).today();
        int completed = (int) tasks.forDay(user, today).stream().filter(TaskItem::isDone).count();
        int moved = reviews.closeDay(user, rating, note, move == null ? List.of() : move, completed);
        flash.addFlashAttribute("toast", moved > 0
                ? "Day closed. " + moved + (moved == 1 ? " task" : " tasks") + " moved to tomorrow."
                : "Day closed. Enjoy your evening.");
        return "redirect:/";
    }
}
