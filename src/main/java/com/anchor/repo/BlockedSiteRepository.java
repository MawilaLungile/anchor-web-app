package com.anchor.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.anchor.domain.BlockedSite;
import com.anchor.domain.SiteList;

public interface BlockedSiteRepository extends JpaRepository<BlockedSite, Long> {

    List<BlockedSite> findByUserIdOrderByDomainAsc(Long userId);

    List<BlockedSite> findByUserIdAndListOrderByDomainAsc(Long userId, SiteList list);

    boolean existsByUserIdAndDomainAndList(Long userId, String domain, SiteList list);

    Optional<BlockedSite> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndList(Long userId, SiteList list);

    @Modifying
    @Query("delete from BlockedSite b where b.userId = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
