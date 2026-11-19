package com.anchor.util;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Time as seen by one user: "today" and hours of the day depend on their time zone. */
public final class UserClock {

    private final Clock clock;
    private final ZoneId zone;

    public UserClock(Clock clock, String timeZone) {
        this.clock = clock;
        this.zone = parseZone(timeZone);
    }

    public static ZoneId parseZone(String timeZone) {
        try {
            return timeZone == null || timeZone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }

    public ZoneId zone() {
        return zone;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZonedDateTime nowLocal() {
        return ZonedDateTime.ofInstant(clock.instant(), zone);
    }

    public LocalDate today() {
        return nowLocal().toLocalDate();
    }

    public Instant startOf(LocalDate day) {
        return day.atStartOfDay(zone).toInstant();
    }

    public LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, zone);
    }

    public LocalDate date(Instant instant) {
        return local(instant).toLocalDate();
    }
}
