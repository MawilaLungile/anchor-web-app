package com.anchor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "blocked_sites")
public class BlockedSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "domain_name")
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type")
    private SiteList list = SiteList.FOCUS;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "removal_requested_at")
    private Instant removalRequestedAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public SiteList getList() {
        return list;
    }

    public void setList(SiteList list) {
        this.list = list;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getRemovalRequestedAt() {
        return removalRequestedAt;
    }

    public void setRemovalRequestedAt(Instant removalRequestedAt) {
        this.removalRequestedAt = removalRequestedAt;
    }
}
