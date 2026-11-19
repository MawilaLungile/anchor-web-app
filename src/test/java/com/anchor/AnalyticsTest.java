package com.anchor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.anchor.domain.EnergyEntry;
import com.anchor.domain.FocusSession;
import com.anchor.domain.SessionStatus;
import com.anchor.service.Analytics;

class AnalyticsTest {

    private static EnergyEntry energy(int day, int hour, int level) {
        EnergyEntry e = new EnergyEntry();
        e.setRecordedAt(LocalDateTime.of(2026, 11, day, hour, 15).toInstant(ZoneOffset.UTC));
        e.setLevel(level);
        return e;
    }

    private static FocusSession session(LocalDate day, SessionStatus status) {
        FocusSession s = new FocusSession();
        s.setStartedAt(day.atTime(9, 0).toInstant(ZoneOffset.UTC));
        s.setPlannedMinutes(50);
        s.setEndedAt(s.getStartedAt().plusSeconds(50 * 60));
        s.setStatus(status);
        return s;
    }

    @Test
    void findsThePeakWindow() {
        List<EnergyEntry> entries = new ArrayList<>();
        for (int d = 2; d <= 6; d++) {
            entries.add(energy(d, 9, 5));
            entries.add(energy(d, 10, 5));
            entries.add(energy(d, 14, 2));
            entries.add(energy(d, 15, 2));
        }
        Analytics.Window peak = Analytics.findWindow(entries, e -> LocalDateTime.ofInstant(e.getRecordedAt(), ZoneOffset.UTC), true);
        Analytics.Window dip = Analytics.findWindow(entries, e -> LocalDateTime.ofInstant(e.getRecordedAt(), ZoneOffset.UTC), false);

        assertThat(peak).isNotNull();
        assertThat(peak.startHour()).isEqualTo(9);
        assertThat(dip).isNotNull();
        assertThat(dip.startHour()).isEqualTo(14);
    }

    @Test
    void needsEnoughDataForAWindow() {
        List<EnergyEntry> few = List.of(energy(2, 9, 5), energy(2, 10, 4));
        assertThat(Analytics.findWindow(few, e -> LocalDateTime.ofInstant(e.getRecordedAt(), ZoneOffset.UTC), true)).isNull();
    }

    @Test
    void keptRateIgnoresActiveSessions() {
        LocalDate day = LocalDate.of(2026, 11, 10);
        List<FocusSession> sessions = List.of(session(day, SessionStatus.KEPT), session(day, SessionStatus.KEPT),
                session(day, SessionStatus.BROKEN), session(day, SessionStatus.ACTIVE));
        assertThat(Analytics.keptRate(sessions)).isEqualTo(2 / 3d);
    }

    @Test
    void focusStreakCountsConsecutiveDays() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        List<FocusSession> sessions = List.of(
                session(today, SessionStatus.KEPT),
                session(today.minusDays(1), SessionStatus.KEPT),
                session(today.minusDays(2), SessionStatus.KEPT),
                session(today.minusDays(4), SessionStatus.KEPT));
        int streak = Analytics.focusDayStreak(sessions, s -> LocalDate.ofInstant(s.getStartedAt(), ZoneOffset.UTC), today);
        assertThat(streak).isEqualTo(3);
    }

    @Test
    void heatmapAveragesByWeekdayAndHour() {
        // 2 Nov 2026 is a Monday.
        List<EnergyEntry> entries = List.of(energy(2, 9, 4), energy(2, 9, 2));
        double[][] cells = Analytics.heatmap(entries, e -> LocalDateTime.ofInstant(e.getRecordedAt(), ZoneOffset.UTC));
        assertThat(cells[0][9 - Analytics.FIRST_HOUR]).isEqualTo(3.0);
        assertThat(cells[1][0]).isZero();
    }

    @Test
    void milestonesStepUp() {
        assertThat(Analytics.nextMilestone(0)).isEqualTo(1);
        assertThat(Analytics.nextMilestone(12)).isEqualTo(14);
        assertThat(Analytics.nextMilestone(30)).isEqualTo(60);
    }

    @Test
    void sessionTimesAreCapped() {
        FocusSession s = session(LocalDate.of(2026, 11, 10), SessionStatus.KEPT);
        s.setEndedAt(s.getStartedAt().plusSeconds(3 * 3600));
        assertThat(s.getFocusedMinutes()).isEqualTo(50);
        assertThat(s.getPlannedEnd()).isEqualTo(s.getStartedAt().plusSeconds(3000));
        assertThat(Instant.EPOCH).isBefore(s.getPlannedEnd());
    }
}
