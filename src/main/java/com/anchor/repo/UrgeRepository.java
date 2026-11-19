package com.anchor.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.UrgeEntry;

public interface UrgeRepository extends JpaRepository<UrgeEntry, Long> {

    List<UrgeEntry> findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(Long userId, Instant from);

    List<UrgeEntry> findByUserId(Long userId);

    @Modifying
    @Query("delete from UrgeEntry u where u.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
