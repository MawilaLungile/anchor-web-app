package com.anchor.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.TaskItem;

public interface TaskRepository extends JpaRepository<TaskItem, Long> {

    List<TaskItem> findByUserIdAndPlannedForOrderByCreatedAtAsc(Long userId, LocalDate plannedFor);

    List<TaskItem> findByUserIdAndPlannedForGreaterThanEqual(Long userId, LocalDate from);

    List<TaskItem> findByUserIdAndDoneFalseAndPlannedForBetween(Long userId, LocalDate from, LocalDate to);

    List<TaskItem> findByUserId(Long userId);

    Optional<TaskItem> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("delete from TaskItem t where t.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
