package com.anchor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.anchor.util.Domains;
import com.anchor.util.Fmt;

class DomainsTest {

    @Test
    void normalizesLinksToDomains() {
        assertThat(Domains.normalize("https://www.YouTube.com/watch?v=1")).isEqualTo("youtube.com");
        assertThat(Domains.normalize("reddit.com/r/java")).isEqualTo("reddit.com");
        assertThat(Domains.normalize("m.facebook.com:443")).isEqualTo("m.facebook.com");
    }

    @Test
    void rejectsGarbage() {
        assertThat(Domains.normalize("not a site")).isNull();
        assertThat(Domains.normalize("")).isNull();
        assertThat(Domains.normalize("localhost")).isNull();
    }

    @Test
    void neverBlocksCriticalDomains() {
        assertThat(Domains.isSafeListed("update.microsoft.com")).isTrue();
        assertThat(Domains.isSafeListed("google.com")).isTrue();
        assertThat(Domains.isSafeListed("notgoogle.com")).isFalse();
    }

    @Test
    void formatsHumanFriendlyValues() {
        assertThat(Fmt.minutes(45)).isEqualTo("45 min");
        assertThat(Fmt.minutes(90)).isEqualTo("1 h 30 min");
        assertThat(Fmt.hours(120)).isEqualTo("2 h");
        assertThat(Fmt.hours(150)).isEqualTo("2.5 h");
        assertThat(Fmt.initials("Lungile Mawila")).isEqualTo("LM");
        assertThat(Fmt.percent(0.666)).isEqualTo("67%");
    }
}
