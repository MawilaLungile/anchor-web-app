package com.anchor.util;

import java.time.Duration;
import java.util.Locale;

/** Human-friendly formatting helpers used by services and templates. */
public final class Fmt {

    private Fmt() {
    }

    public static String minutes(long minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    public static String hours(double minutes) {
        double hours = minutes / 60d;
        return hours < 10
                ? String.format(Locale.ROOT, "%.1f h", hours).replace(".0 h", " h")
                : Math.round(hours) + " h";
    }

    public static String percent(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    public static String days(long days) {
        return days == 1 ? "1 day" : days + " days";
    }

    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String hour(int hour) {
        return String.format(Locale.ROOT, "%02d:00", hour);
    }

    public static String relative(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " h ago";
        }
        return (hours / 24) + " d ago";
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "A";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }
}
