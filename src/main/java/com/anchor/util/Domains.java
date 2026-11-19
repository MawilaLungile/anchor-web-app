package com.anchor.util;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Domain normalisation and the safe-list of domains that must never be blocked. */
public final class Domains {

    private static final Pattern DOMAIN = Pattern.compile(
            "^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}$");

    private static final List<String> SAFE_SUFFIXES = List.of(
            "microsoft.com", "windows.com", "windowsupdate.com", "live.com", "office.com",
            "google.com", "googleapis.com", "gstatic.com", "android.com", "apple.com", "icloud.com",
            "cloudflare.com", "github.com", "githubusercontent.com");

    private Domains() {
    }

    /** "https://www.YouTube.com/watch?v=1" becomes "youtube.com". Returns null when invalid. */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            s = s.substring(scheme + 3);
        }
        for (char c : new char[] {'/', '?', '#', ':'}) {
            int cut = s.indexOf(c);
            if (cut >= 0) {
                s = s.substring(0, cut);
            }
        }
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        return DOMAIN.matcher(s).matches() ? s : null;
    }

    public static boolean isSafeListed(String domain) {
        return SAFE_SUFFIXES.stream().anyMatch(s -> domain.equals(s) || domain.endsWith("." + s));
    }
}
