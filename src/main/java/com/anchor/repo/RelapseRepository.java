package com.anchor.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.RelapseEntry;

public interface RelapseRepository extends JpaRepository<RelapseEntry, Long> {

    List<RelapseEntry> findByUserIdOrderByRecordedAtDesc(Long userId);

    @Modifying
    @Query("delete from RelapseEntry r where r.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
