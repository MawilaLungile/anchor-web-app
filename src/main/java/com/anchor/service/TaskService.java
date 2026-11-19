package com.anchor.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.TaskItem;
import com.anchor.domain.TaskSlot;
import com.anchor.repo.TaskRepository;
import com.anchor.util.UserClock;

@Service
public class TaskService {

    public static final int MAX_NICE_TO_HAVES = 3;

    private final TaskRepository tasks;
    private final UserService users;

    public TaskService(TaskRepository tasks, UserService users) {
        this.tasks = tasks;
        this.users = users;
    }

    public List<TaskItem> forDay(AppUser user, LocalDate day) {
        return tasks.findByUserIdAndPlannedForOrderByCreatedAtAsc(user.getId(), day);
    }

    public List<TaskItem> since(AppUser user, LocalDate from) {
        return tasks.findByUserIdAndPlannedForGreaterThanEqual(user.getId(), from);
    }

    public TaskItem get(AppUser user, Long id) {
        return tasks.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new NotFoundException("Task"));
    }

    /** Open tasks from the last seven days that were never finished. */
    public List<TaskItem> overdue(AppUser user) {
        LocalDate today = users.clockFor(user).today();
        return tasks.findByUserIdAndDoneFalseAndPlannedForBetween(user.getId(), today.minusDays(7), today.minusDays(1));
    }

    @Transactional
    public TaskItem save(AppUser user, TaskItem task) {
        UserClock clock = users.clockFor(user);
        task.setUserId(user.getId());
        if (task.getCreatedAt() == null) {
            task.setCreatedAt(clock.now());
        }
        TaskItem saved = tasks.save(task);
        if (saved.getSlot() == TaskSlot.ONE_THING) {
            demoteOtherOneThings(user, saved.getPlannedFor(), saved.getId());
        }
        return saved;
    }

    @Transactional
    public void toggle(AppUser user, Long id) {
        TaskItem task = get(user, id);
        task.setDone(!task.isDone());
        task.setCompletedAt(task.isDone() ? users.clockFor(user).now() : null);
        tasks.save(task);
    }

    @Transactional
    public void markDone(AppUser user, Long id) {
        tasks.findByIdAndUserId(id, user.getId()).ifPresent(task -> {
            if (!task.isDone()) {
                task.setDone(true);
                task.setCompletedAt(users.clockFor(user).now());
                tasks.save(task);
            }
        });
    }

    @Transactional
    public void delete(AppUser user, Long id) {
        tasks.delete(get(user, id));
    }

    @Transactional
    public int carryOverToToday(AppUser user) {
        LocalDate today = users.clockFor(user).today();
        List<TaskItem> open = overdue(user);
        for (TaskItem t : open) {
            t.setPlannedFor(today);
            if (t.getSlot() == TaskSlot.ONE_THING) {
                t.setSlot(TaskSlot.OTHER);
            }
            tasks.save(t);
        }
        return open.size();
    }

    /** Moves unfinished tasks to the next day, keeping only one "One Thing". */
    @Transactional
    public int moveToDay(AppUser user, List<Long> ids, LocalDate day) {
        boolean dayHasOneThing = forDay(user, day).stream().anyMatch(TaskItem::isOneThing);
        int moved = 0;
        for (Long id : ids) {
            TaskItem t = tasks.findByIdAndUserId(id, user.getId()).orElse(null);
            if (t == null || t.isDone()) {
                continue;
            }
            t.setPlannedFor(day);
            if (t.getSlot() == TaskSlot.ONE_THING) {
                if (dayHasOneThing) {
                    t.setSlot(TaskSlot.NICE_TO_HAVE);
                } else {
                    dayHasOneThing = true;
                }
            }
            tasks.save(t);
            moved++;
        }
        return moved;
    }

    private void demoteOtherOneThings(AppUser user, LocalDate day, Long keepId) {
        for (TaskItem t : forDay(user, day)) {
            if (t.getSlot() == TaskSlot.ONE_THING && !t.getId().equals(keepId)) {
                t.setSlot(TaskSlot.NICE_TO_HAVE);
                tasks.save(t);
            }
        }
    }

    public static Comparator<TaskItem> openFirst() {
        return Comparator.comparing(TaskItem::isDone).thenComparing(TaskItem::getCreatedAt);
    }
}
