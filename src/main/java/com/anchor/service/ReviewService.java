package com.anchor.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.DailyReview;
import com.anchor.repo.DailyReviewRepository;

@Service
public class ReviewService {

    public static final List<String> RATINGS = List.of("Rough", "Meh", "Okay", "Good", "Great");

    private final DailyReviewRepository reviews;
    private final TaskService tasks;
    private final UserService users;

    public ReviewService(DailyReviewRepository reviews, TaskService tasks, UserService users) {
        this.reviews = reviews;
        this.tasks = tasks;
        this.users = users;
    }

    public Optional<DailyReview> forDay(AppUser user, LocalDate day) {
        return reviews.findByUserIdAndDate(user.getId(), day);
    }

    /** Saves today's review and moves the chosen unfinished tasks to tomorrow. Returns tasks moved. */
    @Transactional
    public int closeDay(AppUser user, int rating, String note, List<Long> moveIds, int completedCount) {
        LocalDate today = users.clockFor(user).today();
        int moved = tasks.moveToDay(user, moveIds, today.plusDays(1));

        DailyReview review = reviews.findByUserIdAndDate(user.getId(), today).orElseGet(DailyReview::new);
        review.setUserId(user.getId());
        review.setDate(today);
        review.setRating(Math.max(1, Math.min(5, rating)));
        String trimmed = note == null ? null : note.trim();
        review.setNote(trimmed == null || trimmed.isEmpty() ? null : trimmed.substring(0, Math.min(1000, trimmed.length())));
        review.setCompletedCount(completedCount);
        review.setMovedCount(moved);
        reviews.save(review);
        return moved;
    }
}
