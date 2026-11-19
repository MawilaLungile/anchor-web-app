package com.anchor.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.FocusSession;
import com.anchor.domain.SessionStatus;
import com.anchor.repo.FocusSessionRepository;

/**
 * Commitment sessions. End times are absolute, so a session survives closed tabs and server
 * restarts; a session whose time has passed is completed (kept) the next time it's looked at.
 */
@Service
public class FocusService {

    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 240;

    private final FocusSessionRepository sessions;
    private final TaskService tasks;
    private final UserService users;

    public FocusService(FocusSessionRepository sessions, TaskService tasks, UserService users) {
        this.sessions = sessions;
        this.tasks = tasks;
        this.users = users;
    }

    @Transactional
    public Optional<FocusSession> active(AppUser user) {
        Optional<FocusSession> active = sessions.findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), SessionStatus.ACTIVE);
        Instant now = users.clockFor(user).now();
        if (active.isPresent() && !now.isBefore(active.get().getPlannedEnd())) {
            finish(user, active.get(), SessionStatus.KEPT, null, active.get().getPlannedEnd());
            return Optional.empty();
        }
        return active;
    }

    @Transactional
    public FocusSession start(AppUser user, String intention, int minutes, Long taskId, boolean block) {
        Optional<FocusSession> running = active(user);
        if (running.isPresent()) {
            return running.get();
        }

        FocusSession s = new FocusSession();
        s.setUserId(user.getId());
        s.setIntention(intention == null || intention.isBlank() ? "Deep work" : intention.trim());
        s.setPlannedMinutes(Math.max(MIN_MINUTES, Math.min(minutes, MAX_MINUTES)));
        if (taskId != null) {
            // Only link tasks the user owns.
            s.setTaskId(tasks.get(user, taskId).getId());
        }
        s.setStartedAt(users.clockFor(user).now());
        s.setStatus(SessionStatus.ACTIVE);
        s.setBlocksDistractions(block);
        return sessions.save(s);
    }

    @Transactional
    public void extend(AppUser user, int minutes) {
        active(user).ifPresent(s -> {
            s.setPlannedMinutes(Math.min(s.getPlannedMinutes() + minutes, 300));
            sessions.save(s);
        });
    }

    @Transactional
    public void complete(AppUser user) {
        sessions.findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), SessionStatus.ACTIVE).ifPresent(s -> {
            Instant now = users.clockFor(user).now();
            // Only count as kept once the planned time is (almost) up.
            Instant end = now.isBefore(s.getPlannedEnd().minusSeconds(5)) ? null : s.getPlannedEnd();
            if (end != null) {
                finish(user, s, SessionStatus.KEPT, null, end);
            }
        });
    }

    @Transactional
    public void breakEarly(AppUser user, String reason) {
        sessions.findFirstByUserIdAndStatusOrderByStartedAtDesc(user.getId(), SessionStatus.ACTIVE).ifPresent(s -> {
            String text = reason == null || reason.isBlank() ? "No reason given" : reason.trim();
            if (text.length() > 300) {
                text = text.substring(0, 300);
            }
            finish(user, s, SessionStatus.BROKEN, text, users.clockFor(user).now());
        });
    }

    public List<FocusSession> since(AppUser user, Instant from) {
        return sessions.findByUserIdAndStartedAtGreaterThanEqualOrderByStartedAtDesc(user.getId(), from);
    }

    private void finish(AppUser user, FocusSession s, SessionStatus status, String reason, Instant endedAt) {
        s.setStatus(status);
        s.setBreakReason(reason);
        s.setEndedAt(endedAt);
        sessions.save(s);
        if (status == SessionStatus.KEPT && s.getTaskId() != null) {
            tasks.markDone(user, s.getTaskId());
        }
    }
}
