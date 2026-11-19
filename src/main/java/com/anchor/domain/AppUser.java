package com.anchor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email")
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "time_zone")
    private String timeZone;

    @Column(name = "theme")
    private String theme = "system";

    @Column(name = "default_focus_minutes")
    private int defaultFocusMinutes = 50;

    @Column(name = "block_during_focus")
    private boolean blockDuringFocus = true;

    @Column(name = "cooling_off_minutes")
    private int coolingOffMinutes = 30;

    @Column(name = "streak_start")
    private Instant streakStart;

    @Column(name = "longest_streak_days")
    private int longestStreakDays;

    @Column(name = "onboarded")
    private boolean onboarded;

    @Column(name = "created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public int getDefaultFocusMinutes() {
        return defaultFocusMinutes;
    }

    public void setDefaultFocusMinutes(int defaultFocusMinutes) {
        this.defaultFocusMinutes = defaultFocusMinutes;
    }

    public boolean isBlockDuringFocus() {
        return blockDuringFocus;
    }

    public void setBlockDuringFocus(boolean blockDuringFocus) {
        this.blockDuringFocus = blockDuringFocus;
    }

    public int getCoolingOffMinutes() {
        return coolingOffMinutes;
    }

    public void setCoolingOffMinutes(int coolingOffMinutes) {
        this.coolingOffMinutes = coolingOffMinutes;
    }

    public Instant getStreakStart() {
        return streakStart;
    }

    public void setStreakStart(Instant streakStart) {
        this.streakStart = streakStart;
    }

    public int getLongestStreakDays() {
        return longestStreakDays;
    }

    public void setLongestStreakDays(int longestStreakDays) {
        this.longestStreakDays = longestStreakDays;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
