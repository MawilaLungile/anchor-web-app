package com.anchor.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.FocusSession;
import com.anchor.domain.SessionStatus;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    Optional<FocusSession> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, SessionStatus status);

    List<FocusSession> findByUserIdAndStartedAtGreaterThanEqualOrderByStartedAtDesc(Long userId, Instant from);

    List<FocusSession> findByUserId(Long userId);

    @Modifying
    @Query("delete from FocusSession s where s.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
