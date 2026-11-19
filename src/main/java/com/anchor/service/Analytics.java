package com.anchor.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.anchor.domain.EnergyEntry;
import com.anchor.domain.FocusSession;
import com.anchor.domain.SessionStatus;
import com.anchor.util.Fmt;

/** Pure functions that turn raw entries into insights. No Spring, easy to test. */
public final class Analytics {

    public static final int FIRST_HOUR = 6;
    public static final int LAST_HOUR = 22;
    public static final int HOUR_COUNT = LAST_HOUR - FIRST_HOUR + 1;

    private Analytics() {
    }

    public record Window(int startHour, int endHour, double average) {
        public String text() {
            return Fmt.hour(startHour) + " – " + Fmt.hour(endHour);
        }
    }

    /** Average level for each hour of the day (null when there's no data). */
    public static Double[] hourlyAverages(List<EnergyEntry> entries, Function<EnergyEntry, LocalDateTime> toLocal) {
        double[] sum = new double[24];
        int[] count = new int[24];
        for (EnergyEntry e : entries) {
            int h = toLocal.apply(e).getHour();
            sum[h] += e.getLevel();
            count[h]++;
        }
        Double[] avg = new Double[24];
        for (int h = 0; h < 24; h++) {
            avg[h] = count[h] == 0 ? null : sum[h] / count[h];
        }
        return avg;
    }

    /** Best (or worst) two-hour window; null until there are enough check-ins. */
    public static Window findWindow(List<EnergyEntry> entries, Function<EnergyEntry, LocalDateTime> toLocal, boolean best) {
        if (entries.size() < 6) {
            return null;
        }
        Double[] hourly = hourlyAverages(entries, toLocal);
        Window pick = null;
        for (int h = FIRST_HOUR; h < LAST_HOUR; h++) {
            if (hourly[h] == null || hourly[h + 1] == null) {
                continue;
            }
            double avg = (hourly[h] + hourly[h + 1]) / 2;
            if (pick == null || (best ? avg > pick.average() : avg < pick.average())) {
                pick = new Window(h, h + 2, avg);
            }
        }
        return pick;
    }

    /** Rows Monday..Sunday, columns FIRST_HOUR..LAST_HOUR; average level or 0 for no data. */
    public static double[][] heatmap(List<EnergyEntry> entries, Function<EnergyEntry, LocalDateTime> toLocal) {
        double[][] sum = new double[7][HOUR_COUNT];
        int[][] count = new int[7][HOUR_COUNT];
        for (EnergyEntry e : entries) {
            LocalDateTime t = toLocal.apply(e);
            int h = t.getHour();
            if (h < FIRST_HOUR || h > LAST_HOUR) {
                continue;
            }
            int d = t.getDayOfWeek().getValue() - 1;
            sum[d][h - FIRST_HOUR] += e.getLevel();
            count[d][h - FIRST_HOUR]++;
        }
        double[][] cells = new double[7][HOUR_COUNT];
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < HOUR_COUNT; h++) {
                cells[d][h] = count[d][h] == 0 ? 0 : sum[d][h] / count[d][h];
            }
        }
        return cells;
    }

    public static List<Double> focusMinutesPerDay(List<FocusSession> sessions, Function<FocusSession, LocalDate> day,
                                                  LocalDate lastDay, int days) {
        LocalDate start = lastDay.minusDays(days - 1L);
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = start.plusDays(i);
            double total = sessions.stream()
                    .filter(s -> s.getStatus() != SessionStatus.ACTIVE && day.apply(s).equals(d))
                    .mapToDouble(FocusSession::getFocusedMinutes)
                    .sum();
            result.add(total);
        }
        return result;
    }

    public static double keptRate(List<FocusSession> sessions) {
        long finished = sessions.stream().filter(FocusSession::isFinished).count();
        if (finished == 0) {
            return 0;
        }
        long kept = sessions.stream().filter(FocusSession::isKept).count();
        return kept / (double) finished;
    }

    /** Consecutive days (ending today or yesterday) with at least one kept session. */
    public static int focusDayStreak(List<FocusSession> sessions, Function<FocusSession, LocalDate> day, LocalDate today) {
        Set<LocalDate> days = new HashSet<>();
        sessions.stream().filter(FocusSession::isKept).forEach(s -> days.add(day.apply(s)));
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }

    /** Next streak milestone above the current number of days. */
    public static int nextMilestone(long days) {
        for (int m : new int[] {1, 3, 7, 14, 30, 60, 90, 180, 365, 730}) {
            if (m > days) {
                return m;
            }
        }
        return (int) days + 365;
    }
}
