package com.anchor.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.EnergyEntry;

public interface EnergyRepository extends JpaRepository<EnergyEntry, Long> {

    List<EnergyEntry> findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(Long userId, Instant from);

    List<EnergyEntry> findByUserId(Long userId);

    @Modifying
    @Query("delete from EnergyEntry e where e.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
