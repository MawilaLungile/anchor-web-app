package com.anchor.web;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.anchor.domain.AppUser;
import com.anchor.domain.EnergyEntry;
import com.anchor.domain.FocusSession;
import com.anchor.domain.TaskItem;
import com.anchor.domain.UrgeEntry;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.Analytics;
import com.anchor.service.EnergyService;
import com.anchor.service.FocusService;
import com.anchor.service.ShieldService;
import com.anchor.service.TaskService;
import com.anchor.service.UserService;
import com.anchor.util.Fmt;
import com.anchor.util.UserClock;

@Controller
public class InsightsController {

    public record Bar(String label, String value, int heightPercent, boolean highlight, boolean today) {
    }

    public record HeatCell(String color, String title) {
    }

    public record HeatRow(String label, List<HeatCell> cells) {
    }

    public record TriggerStat(String name, String icon, long count, int percent) {
    }

    private final CurrentUser current;
    private final UserService users;
    private final FocusService focus;
    private final TaskService tasks;
    private final EnergyService energy;
    private final ShieldService shield;

    public InsightsController(CurrentUser current, UserService users, FocusService focus, TaskService tasks,
                              EnergyService energy, ShieldService shield) {
        this.current = current;
        this.users = users;
        this.focus = focus;
        this.tasks = tasks;
        this.energy = energy;
        this.shield = shield;
    }

    @GetMapping("/insights")
    public String page(@AuthenticationPrincipal AnchorPrincipal principal,
                       @RequestParam(defaultValue = "7") int days, Model model) {
        int range = days == 30 ? 30 : 7;
        AppUser user = current.of(principal);
        UserClock clock = users.clockFor(user);
        LocalDate today = clock.today();
        LocalDate since = today.minusDays(range - 1L);

        List<FocusSession> sessions = focus.since(user, clock.startOf(since)).stream().filter(FocusSession::isFinished).toList();
        List<TaskItem> taskList = tasks.since(user, since);
        List<UrgeEntry> urges = shield.urgesSince(user, clock.startOf(since));
        List<EnergyEntry> energyList = energy.since(user, clock.startOf(today.minusDays(27)));

        model.addAttribute("range", range);

        // KPIs
        List<Double> minutes = Analytics.focusMinutesPerDay(sessions, s -> clock.date(s.getStartedAt()), today, range);
        double totalMinutes = minutes.stream().mapToDouble(Double::doubleValue).sum();
        model.addAttribute("focusHours", Fmt.hours(totalMinutes));
        model.addAttribute("keptRate", sessions.isEmpty() ? "–" : Fmt.percent(Analytics.keptRate(sessions)));
        model.addAttribute("tasksDone", taskList.stream().filter(TaskItem::isDone).count());
        model.addAttribute("urges", urges.isEmpty() ? "0" : urges.stream().filter(UrgeEntry::isResisted).count() + "/" + urges.size());
        List<FocusSession> longer = focus.since(user, clock.startOf(today.minusDays(60)));
        model.addAttribute("focusStreak", Fmt.days(Analytics.focusDayStreak(longer, s -> clock.date(s.getStartedAt()), today)));

        // Bars
        double max = minutes.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        int maxIndex = minutes.indexOf(max);
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            LocalDate d = since.plusDays(i);
            String label = range == 7
                    ? (d.equals(today) ? "Today" : d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    : (i % 5 == 0 || d.equals(today) ? d.format(DateTimeFormatter.ofPattern("d/M")) : "");
            double v = minutes.get(i);
            bars.add(new Bar(label, Fmt.minutes(Math.round(v)), max <= 0 ? 0 : (int) Math.round(v / max * 100),
                    max > 0 && i == maxIndex, d.equals(today)));
        }
        model.addAttribute("bars", bars);

        // Heatmap
        double[][] cells = Analytics.heatmap(energyList, e -> clock.local(e.getRecordedAt()));
        List<HeatRow> rows = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            String dayName = DayOfWeek.of(d + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            List<HeatCell> row = new ArrayList<>();
            for (int h = 0; h < Analytics.HOUR_COUNT; h++) {
                double v = cells[d][h];
                String title = dayName + " " + Fmt.hour(Analytics.FIRST_HOUR + h)
                        + (v == 0 ? ": no data" : String.format(Locale.ROOT, ": %.1f / 5", v));
                row.add(new HeatCell(v == 0 ? null : blend(v), title));
            }
            rows.add(new HeatRow(dayName, row));
        }
        model.addAttribute("heatRows", rows);
        List<String> hourLabels = new ArrayList<>();
        for (int h = 0; h < Analytics.HOUR_COUNT; h++) {
            hourLabels.add(h % 3 == 0 ? String.format("%02d", Analytics.FIRST_HOUR + h) : "");
        }
        model.addAttribute("hourLabels", hourLabels);

        Analytics.Window peak = Analytics.findWindow(energyList, e -> clock.local(e.getRecordedAt()), true);
        Analytics.Window dip = Analytics.findWindow(energyList, e -> clock.local(e.getRecordedAt()), false);
        model.addAttribute("peakText", peak == null ? "Not enough data yet" : peak.text());
        model.addAttribute("dipText", dip == null ? "–" : dip.text());
        model.addAttribute("energyAdvice", peak != null && dip != null
                ? "Plan deep work for " + peak.text() + ". Around " + dip.text() + " your energy dips, which suits email, errands and admin."
                : "Check in a few times a day for a week to reveal your rhythm.");

        // Triggers
        Map<String, Long> counts = urges.stream().collect(Collectors.groupingBy(UrgeEntry::getTrigger, LinkedHashMap::new, Collectors.counting()));
        List<TriggerStat> triggers = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new TriggerStat(e.getKey(), iconFor(e.getKey()), e.getValue(),
                        (int) Math.round(e.getValue() * 100.0 / urges.size())))
                .toList();
        model.addAttribute("triggers", triggers);

        // Break reasons
        Map<String, Long> reasons = sessions.stream().filter(FocusSession::isBroken)
                .filter(s -> s.getBreakReason() != null && !s.getBreakReason().isBlank())
                .collect(Collectors.groupingBy(FocusSession::getBreakReason, Collectors.counting()));
        model.addAttribute("reasons", reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getValue() > 1 ? e.getKey() + " (" + e.getValue() + "×)" : e.getKey())
                .toList());

        // Story
        String period = range == 7 ? "this week" : "this month";
        if (sessions.isEmpty()) {
            model.addAttribute("headline", "Your story starts with one session.");
            model.addAttribute("story", "Start a focus commitment and check in on your energy. Anchor will turn it into a picture of how you work best.");
        } else {
            long kept = sessions.stream().filter(FocusSession::isKept).count();
            StringBuilder story = new StringBuilder("You kept " + kept + " of " + sessions.size() + " commitments.");
            if (peak != null) {
                long inPeak = sessions.stream().filter(s -> {
                    int h = clock.local(s.getStartedAt()).getHour();
                    return h >= peak.startHour() && h < peak.endHour();
                }).count();
                story.append(' ').append(inPeak).append(" of them started in your peak window (").append(peak.text()).append(").");
            }
            if (!triggers.isEmpty()) {
                story.append(" \"").append(triggers.get(0).name()).append("\" was your most common urge trigger, so plan for it.");
            }
            model.addAttribute("headline", Fmt.hours(totalMinutes) + " of protected focus " + period + ".");
            model.addAttribute("story", story.toString());
        }
        return "insights";
    }

    private static String iconFor(String trigger) {
        return ShieldService.TRIGGER_ICONS.stream().filter(t -> t.name().equals(trigger))
                .map(ShieldService.Trigger::icon).findFirst().orElse("waves");
    }

    /** Clay → gold → green, by average energy level 1..5. */
    static String blend(double level) {
        double t = Math.max(0, Math.min(1, (level - 1) / 4));
        int[] low = {0xE5, 0x60, 0x4D};
        int[] mid = {0xF2, 0xC9, 0x4C};
        int[] high = {0x2E, 0x9E, 0x6B};
        int[] a = t < 0.5 ? low : mid;
        int[] b = t < 0.5 ? mid : high;
        double u = t < 0.5 ? t * 2 : (t - 0.5) * 2;
        return String.format("#%02X%02X%02X",
                (int) Math.round(a[0] + (b[0] - a[0]) * u),
                (int) Math.round(a[1] + (b[1] - a[1]) * u),
                (int) Math.round(a[2] + (b[2] - a[2]) * u));
    }
}
