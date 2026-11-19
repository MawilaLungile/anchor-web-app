package com.anchor.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.DailyReview;

public interface DailyReviewRepository extends JpaRepository<DailyReview, Long> {

    Optional<DailyReview> findByUserIdAndDate(Long userId, LocalDate date);

    List<DailyReview> findByUserId(Long userId);

    @Modifying
    @Query("delete from DailyReview r where r.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
