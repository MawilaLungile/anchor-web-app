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
@Table(name = "focus_sessions")
public class FocusSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "intention")
    private String intention;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "planned_minutes")
    private int plannedMinutes;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(name = "break_reason")
    private String breakReason;

    @Column(name = "blocks_distractions")
    private boolean blocksDistractions;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getIntention() {
        return intention;
    }

    public void setIntention(String intention) {
        this.intention = intention;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(int plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getBreakReason() {
        return breakReason;
    }

    public void setBreakReason(String breakReason) {
        this.breakReason = breakReason;
    }

    public boolean isBlocksDistractions() {
        return blocksDistractions;
    }

    public void setBlocksDistractions(boolean blocksDistractions) {
        this.blocksDistractions = blocksDistractions;
    }

    public Instant getPlannedEnd() {
        return startedAt.plusSeconds(plannedMinutes * 60L);
    }

    /** Minutes actually spent, capped at the plan. */
    public int getFocusedMinutes() {
        Instant end = endedAt != null ? endedAt : Instant.now();
        long minutes = java.time.Duration.between(startedAt, end).toMinutes();
        return (int) Math.max(0, Math.min(minutes, plannedMinutes));
    }

    public boolean isKept() {
        return status == SessionStatus.KEPT;
    }

    public boolean isBroken() {
        return status == SessionStatus.BROKEN;
    }

    public boolean isFinished() {
        return status != SessionStatus.ACTIVE;
    }
}
