package com.anchor.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.DailyReview;
import com.anchor.domain.EnergyEntry;
import com.anchor.domain.FocusSession;
import com.anchor.domain.RelapseEntry;
import com.anchor.domain.SessionStatus;
import com.anchor.domain.SiteList;
import com.anchor.domain.TaskItem;
import com.anchor.domain.TaskSlot;
import com.anchor.domain.UrgeEntry;
import com.anchor.repo.BlockedSiteRepository;
import com.anchor.repo.DailyReviewRepository;
import com.anchor.repo.EnergyRepository;
import com.anchor.repo.FocusSessionRepository;
import com.anchor.repo.RelapseRepository;
import com.anchor.repo.TaskRepository;
import com.anchor.repo.UrgeRepository;
import com.anchor.util.UserClock;

/** Sample data, erasing and exporting a user's data. */
@Service
public class DataService {

    private final TaskRepository tasks;
    private final FocusSessionRepository sessions;
    private final EnergyRepository energy;
    private final UrgeRepository urges;
    private final RelapseRepository relapses;
    private final DailyReviewRepository reviews;
    private final BlockedSiteRepository sites;
    private final UserService users;
    private final ShieldService shield;

    public DataService(TaskRepository tasks, FocusSessionRepository sessions, EnergyRepository energy,
                       UrgeRepository urges, RelapseRepository relapses, DailyReviewRepository reviews,
                       BlockedSiteRepository sites, UserService users, ShieldService shield) {
        this.tasks = tasks;
        this.sessions = sessions;
        this.energy = energy;
        this.urges = urges;
        this.relapses = relapses;
        this.reviews = reviews;
        this.sites = sites;
        this.users = users;
        this.shield = shield;
    }

    @Transactional
    public void erase(AppUser user, boolean keepSites) {
        Long id = user.getId();
        tasks.deleteAllForUser(id);
        sessions.deleteAllForUser(id);
        energy.deleteAllForUser(id);
        urges.deleteAllForUser(id);
        relapses.deleteAllForUser(id);
        reviews.deleteAllForUser(id);
        if (!keepSites) {
            sites.deleteAllForUser(id);
        }
    }

    @Transactional
    public void ensureDefaultSites(AppUser user) {
        if (shield.count(user, SiteList.FOCUS) == 0) {
            shield.addPreset(user, 0);
            shield.addPreset(user, 1);
        }
    }

    public Map<String, Object> export(AppUser user) {
        Long id = user.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "Anchor");
        data.put("exportedAt", Instant.now().toString());
        data.put("account", Map.of("email", user.getEmail(), "displayName", user.getDisplayName(), "timeZone", user.getTimeZone()));
        data.put("tasks", tasks.findByUserId(id));
        data.put("sessions", sessions.findByUserId(id));
        data.put("energy", energy.findByUserId(id));
        data.put("urges", urges.findByUserId(id));
        data.put("reviews", reviews.findByUserId(id));
        data.put("sites", sites.findByUserIdOrderByDomainAsc(id));
        data.put("relapses", relapses.findByUserIdOrderByRecordedAtDesc(id));
        return data;
    }

    /** Three weeks of realistic, deterministic history in the user's own time zone. */
    @Transactional
    public void loadSample(AppUser user) {
        erase(user, true);
        ensureDefaultSites(user);

        UserClock clock = users.clockFor(user);
        ZoneId zone = clock.zone();
        Instant now = clock.now();
        LocalDate today = clock.today();
        Random rnd = new Random(1717);
        Long uid = user.getId();

        // Energy: morning peak, post-lunch dip, smaller afternoon lift.
        List<EnergyEntry> energyList = new ArrayList<>();
        for (int d = 21; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            for (int hour : new int[] {7, 9, 10, 11, 13, 14, 16, 18, 20}) {
                if (rnd.nextDouble() < 0.35) {
                    continue;
                }
                Instant at = day.atTime(hour, rnd.nextInt(50)).atZone(zone).toInstant();
                if (at.isAfter(now)) {
                    continue;
                }
                double base = switch (hour) {
                    case 7 -> 3.0;
                    case 9 -> 4.4;
                    case 10 -> 4.6;
                    case 11 -> 4.0;
                    case 13 -> 2.3;
                    case 14 -> 2.6;
                    case 16 -> 3.6;
                    case 18 -> 3.0;
                    default -> 2.4;
                };
                if (weekend) {
                    base -= 0.4;
                }
                EnergyEntry e = new EnergyEntry();
                e.setUserId(uid);
                e.setRecordedAt(at);
                e.setLevel((int) Math.round(Math.max(1, Math.min(5, base + (rnd.nextDouble() - 0.5) * 1.6))));
                energyList.add(e);
            }
        }
        energy.saveAll(energyList);

        // Focus sessions
        String[] intentions = {"Write the quarterly report", "Refactor the sync module", "Study for the Spring certification",
                "Design the onboarding flow", "Inbox zero", "Prepare client presentation", "Read: Deep Work, chapter 3",
                "Plan next sprint", "Fix the login bug"};
        String[] reasons = {"Urgent call from my manager", "Got distracted by my phone", "Too tired to continue", "Meeting moved earlier"};
        List<FocusSession> sessionList = new ArrayList<>();
        for (int d = 20; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            int count = weekend ? rnd.nextInt(2) : 1 + rnd.nextInt(3);
            for (int i = 0; i < count; i++) {
                int minutes = new int[] {25, 50, 50, 90}[rnd.nextInt(4)];
                LocalDateTime startLocal = day.atTime(new int[] {9, 10, 14, 16}[i % 4], rnd.nextInt(30));
                Instant start = startLocal.atZone(zone).toInstant();
                boolean broken = rnd.nextDouble() < 0.18;
                FocusSession s = new FocusSession();
                s.setUserId(uid);
                s.setIntention(intentions[rnd.nextInt(intentions.length)]);
                s.setPlannedMinutes(minutes);
                s.setStartedAt(start);
                s.setEndedAt(start.plusSeconds((broken ? 5 + rnd.nextInt(minutes - 8) : minutes) * 60L));
                s.setStatus(broken ? SessionStatus.BROKEN : SessionStatus.KEPT);
                s.setBreakReason(broken ? reasons[rnd.nextInt(reasons.length)] : null);
                s.setBlocksDistractions(rnd.nextDouble() < 0.8);
                sessionList.add(s);
            }
        }
        if (clock.nowLocal().getHour() >= 10) {
            Instant start = today.atTime(9, 5).atZone(zone).toInstant();
            FocusSession s = new FocusSession();
            s.setUserId(uid);
            s.setIntention("Outline the product strategy memo");
            s.setPlannedMinutes(50);
            s.setStartedAt(start);
            s.setEndedAt(start.plusSeconds(50 * 60));
            s.setStatus(SessionStatus.KEPT);
            s.setBlocksDistractions(true);
            sessionList.add(s);
        }
        sessions.saveAll(sessionList);

        // Today's plan
        List<TaskItem> taskList = new ArrayList<>();
        boolean afternoon = clock.nowLocal().getHour() >= 13;
        taskList.add(task(uid, today, "Finish the product strategy memo", TaskSlot.ONE_THING, true, 90, false, now,
                "Focus on the pricing section and the 2027 roadmap."));
        taskList.add(task(uid, today, "Review Thandi's pull request", TaskSlot.NICE_TO_HAVE, true, 30, false, now, null));
        taskList.add(task(uid, today, "30-minute walk", TaskSlot.NICE_TO_HAVE, false, 30, afternoon, now, null));
        taskList.add(task(uid, today, "Book dentist appointment", TaskSlot.OTHER, false, 15, true, now, null));
        taskList.add(task(uid, today, "Reply to recruiter emails", TaskSlot.OTHER, false, 30, false, now, null));
        taskList.add(task(uid, today, "Pay electricity bill", TaskSlot.OTHER, false, 15, false, now, null));
        for (int d = 14; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            int n = 2 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                taskList.add(task(uid, day, intentions[rnd.nextInt(intentions.length)], i == 0 ? TaskSlot.ONE_THING : TaskSlot.OTHER,
                        i == 0, 30, rnd.nextDouble() < 0.8, day.atTime(17, 0).atZone(zone).toInstant(), null));
            }
        }
        tasks.saveAll(taskList);

        // Urges
        String[] triggers = ShieldService.TRIGGERS.toArray(String[]::new);
        int[] weights = {4, 3, 1, 3, 2, 1};
        List<UrgeEntry> urgeList = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            Instant at = today.minusDays(rnd.nextInt(20)).atTime(19 + rnd.nextInt(5), rnd.nextInt(60)).atZone(zone).toInstant();
            if (at.isAfter(now)) {
                continue;
            }
            UrgeEntry u = new UrgeEntry();
            u.setUserId(uid);
            u.setRecordedAt(at);
            u.setTrigger(triggers[weightedPick(rnd, weights)]);
            u.setSource(rnd.nextDouble() < 0.5 ? "session" : "manual");
            u.setResisted(rnd.nextDouble() < 0.9);
            urgeList.add(u);
        }
        urges.saveAll(urgeList);

        // Reviews
        List<DailyReview> reviewList = new ArrayList<>();
        for (int d = 1; d <= 10; d++) {
            DailyReview r = new DailyReview();
            r.setUserId(uid);
            r.setDate(today.minusDays(d));
            r.setRating(3 + rnd.nextInt(3));
            r.setCompletedCount(2 + rnd.nextInt(4));
            r.setMovedCount(rnd.nextInt(3));
            reviewList.add(r);
        }
        reviews.saveAll(reviewList);

        // Streak history
        RelapseEntry relapse = new RelapseEntry();
        relapse.setUserId(uid);
        relapse.setRecordedAt(today.minusDays(34).atStartOfDay(zone).toInstant());
        relapse.setStreakDays(21);
        relapses.save(relapse);
        user.setStreakStart(today.minusDays(12).atTime(22, 0).atZone(zone).toInstant());
        user.setLongestStreakDays(21);
        users.save(user);
    }

    private static TaskItem task(Long uid, LocalDate day, String title, TaskSlot slot, boolean deep, int estimate,
                                 boolean done, Instant created, String notes) {
        TaskItem t = new TaskItem();
        t.setUserId(uid);
        t.setPlannedFor(day);
        t.setTitle(title);
        t.setSlot(slot);
        t.setDeep(deep);
        t.setEstimateMinutes(estimate);
        t.setDone(done);
        t.setCompletedAt(done ? created : null);
        t.setCreatedAt(created);
        t.setNotes(notes);
        return t;
    }

    private static int weightedPick(Random rnd, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int roll = rnd.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            if (roll < weights[i]) {
                return i;
            }
            roll -= weights[i];
        }
        return weights.length - 1;
    }
}
