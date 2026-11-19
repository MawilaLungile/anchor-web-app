package com.anchor.web;

/** Small view helpers used by several pages. */
public final class Views {

    private Views() {
    }

    /** SVG progress ring (r = 42 in a 100x100 viewBox). */
    public record Ring(double fraction) {
        public static final double CIRCUMFERENCE = 2 * Math.PI * 42;

        public String offset() {
            double f = Math.max(0, Math.min(1, fraction));
            return String.format(java.util.Locale.ROOT, "%.2f", CIRCUMFERENCE * (1 - f));
        }

        public String dash() {
            return String.format(java.util.Locale.ROOT, "%.2f", CIRCUMFERENCE);
        }

        public int percent() {
            return (int) Math.round(Math.max(0, Math.min(1, fraction)) * 100);
        }
    }

    /** A labelled option for radio-chip groups. */
    public record Option(String value, String label, String icon, boolean selected) {
    }

    public static String greeting(int hour) {
        if (hour < 12) {
            return "Good morning";
        }
        return hour < 17 ? "Good afternoon" : "Good evening";
    }
}
