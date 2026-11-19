package com.anchor.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.BlockedSite;
import com.anchor.domain.RelapseEntry;
import com.anchor.domain.SiteList;
import com.anchor.domain.UrgeEntry;
import com.anchor.repo.BlockedSiteRepository;
import com.anchor.repo.RelapseRepository;
import com.anchor.repo.UrgeRepository;
import com.anchor.util.Domains;

/**
 * Streak, urges and block lists. Removing a site from the "always" list goes through a cooling-off
 * period: request, wait, then confirm within a short window (or the request expires).
 */
@Service
public class ShieldService {

    public static final Duration CONFIRM_WINDOW = Duration.ofMinutes(15);

    public static final List<String> TRIGGERS = List.of("Bored", "Stressed", "Lonely", "Tired", "Habit", "Other");

    public record Trigger(String name, String icon) {
    }

    public static final List<Trigger> TRIGGER_ICONS = List.of(
            new Trigger("Bored", "hourglass_top"), new Trigger("Stressed", "psychology"),
            new Trigger("Lonely", "favorite"), new Trigger("Tired", "bedtime"),
            new Trigger("Habit", "replay"), new Trigger("Other", "waves"));

    public record Preset(String name, String icon, List<String> domains) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset("Social media", "forum", List.of("facebook.com", "instagram.com", "x.com", "twitter.com",
                    "tiktok.com", "snapchat.com", "reddit.com", "threads.net", "pinterest.com", "tumblr.com")),
            new Preset("Video & streaming", "ondemand_video", List.of("youtube.com", "netflix.com", "twitch.tv",
                    "primevideo.com", "disneyplus.com", "showmax.com", "kick.com")),
            new Preset("News", "newspaper", List.of("news24.com", "iol.co.za", "timeslive.co.za", "bbc.com",
                    "cnn.com", "nytimes.com", "theguardian.com")),
            new Preset("Shopping", "shopping_bag", List.of("takealot.com", "amazon.com", "shein.com", "temu.com",
                    "aliexpress.com", "ebay.com")),
            new Preset("Gaming", "sports_esports", List.of("store.steampowered.com", "steamcommunity.com",
                    "epicgames.com", "roblox.com", "chess.com", "miniclip.com")));

    public enum RemovalState { NONE, COOLING, READY }

    /** A blocked site plus where it is in the cooling-off flow. */
    public record SiteView(Long id, String domain, SiteList list, RemovalState state, long availableAtMillis) {
        public boolean cooling() {
            return state == RemovalState.COOLING;
        }

        public boolean ready() {
            return state == RemovalState.READY;
        }

        public boolean idle() {
            return state == RemovalState.NONE;
        }
    }

    public enum AddResult { ADDED, DUPLICATE, INVALID, SAFE_LISTED }

    private final BlockedSiteRepository sites;
    private final UrgeRepository urges;
    private final RelapseRepository relapses;
    private final UserService users;
    private final BlockListService lists;

    public ShieldService(BlockedSiteRepository sites, UrgeRepository urges, RelapseRepository relapses,
                         UserService users, BlockListService lists) {
        this.sites = sites;
        this.urges = urges;
        this.relapses = relapses;
        this.users = users;
        this.lists = lists;
    }

    // ------------------------------------------------------------------ Streak

    public long streakDays(AppUser user) {
        return Math.max(0, Duration.between(user.getStreakStart(), users.clockFor(user).now()).toDays());
    }

    public long longestStreak(AppUser user) {
        return Math.max(user.getLongestStreakDays(), streakDays(user));
    }

    @Transactional
    public void resetStreak(AppUser user) {
        long days = streakDays(user);
        RelapseEntry r = new RelapseEntry();
        r.setUserId(user.getId());
        r.setRecordedAt(users.clockFor(user).now());
        r.setStreakDays((int) days);
        relapses.save(r);

        user.setLongestStreakDays((int) Math.max(user.getLongestStreakDays(), days));
        user.setStreakStart(users.clockFor(user).now());
        users.save(user);
    }

    // ------------------------------------------------------------------ Urges

    @Transactional
    public void logUrge(AppUser user, String trigger, String source, boolean resisted) {
        UrgeEntry u = new UrgeEntry();
        u.setUserId(user.getId());
        u.setRecordedAt(users.clockFor(user).now());
        u.setTrigger(TRIGGERS.contains(trigger) ? trigger : "Other");
        u.setSource(List.of("manual", "session").contains(source) ? source : "manual");
        u.setResisted(resisted);
        urges.save(u);
    }

    public List<UrgeEntry> urgesSince(AppUser user, Instant from) {
        return urges.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(user.getId(), from);
    }

    // ------------------------------------------------------------------ Sites

    @Transactional
    public List<SiteView> sites(AppUser user, SiteList list) {
        Instant now = users.clockFor(user).now();
        List<SiteView> result = new ArrayList<>();
        for (BlockedSite s : sites.findByUserIdAndListOrderByDomainAsc(user.getId(), list)) {
            RemovalState state = RemovalState.NONE;
            long available = 0;
            if (s.getRemovalRequestedAt() != null) {
                Instant availableAt = s.getRemovalRequestedAt().plus(Duration.ofMinutes(user.getCoolingOffMinutes()));
                if (now.isAfter(availableAt.plus(CONFIRM_WINDOW))) {
                    s.setRemovalRequestedAt(null); // expired: it can't be "pre-armed"
                    sites.save(s);
                } else {
                    state = now.isBefore(availableAt) ? RemovalState.COOLING : RemovalState.READY;
                    available = availableAt.toEpochMilli();
                }
            }
            result.add(new SiteView(s.getId(), s.getDomain(), s.getList(), state, available));
        }
        return result;
    }

    public long count(AppUser user, SiteList list) {
        return sites.countByUserIdAndList(user.getId(), list);
    }

    public boolean hasPendingRemovals(AppUser user) {
        return sites(user, SiteList.ALWAYS).stream().anyMatch(s -> !s.idle());
    }

    @Transactional
    public AddResult add(AppUser user, String input, SiteList list) {
        String domain = Domains.normalize(input);
        if (domain == null) {
            return AddResult.INVALID;
        }
        if (Domains.isSafeListed(domain)) {
            return AddResult.SAFE_LISTED;
        }
        if (sites.existsByUserIdAndDomainAndList(user.getId(), domain, list)) {
            return AddResult.DUPLICATE;
        }
        BlockedSite s = new BlockedSite();
        s.setUserId(user.getId());
        s.setDomain(domain);
        s.setList(list);
        s.setCreatedAt(users.clockFor(user).now());
        sites.save(s);
        return AddResult.ADDED;
    }

    @Transactional
    public int addPreset(AppUser user, int index) {
        if (index < 0 || index >= PRESETS.size()) {
            return 0;
        }
        int added = 0;
        for (String d : PRESETS.get(index).domains()) {
            if (add(user, d, SiteList.FOCUS) == AddResult.ADDED) {
                added++;
            }
        }
        return added;
    }

    /** Focus-list sites go immediately; always-list sites start the cooling-off timer. */
    @Transactional
    public boolean requestRemoval(AppUser user, Long id) {
        BlockedSite s = sites.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new NotFoundException("Site"));
        if (s.getList() == SiteList.FOCUS) {
            sites.delete(s);
            return true;
        }
        s.setRemovalRequestedAt(users.clockFor(user).now());
        sites.save(s);
        return false;
    }

    @Transactional
    public boolean confirmRemoval(AppUser user, Long id) {
        BlockedSite s = sites.findByIdAndUserId(id, user.getId()).orElseThrow(() -> new NotFoundException("Site"));
        if (s.getRemovalRequestedAt() == null) {
            return false;
        }
        Instant now = users.clockFor(user).now();
        Instant availableAt = s.getRemovalRequestedAt().plus(Duration.ofMinutes(user.getCoolingOffMinutes()));
        if (now.isBefore(availableAt) || now.isAfter(availableAt.plus(CONFIRM_WINDOW))) {
            return false;
        }
        sites.delete(s);
        return true;
    }

    @Transactional
    public void cancelRemoval(AppUser user, Long id) {
        sites.findByIdAndUserId(id, user.getId()).ifPresent(s -> {
            s.setRemovalRequestedAt(null);
            sites.save(s);
        });
    }

    // ------------------------------------------------------------------ Hosts file export

    /** Writes a ready-to-use hosts file containing the chosen lists. */
    public void writeHostsFile(AppUser user, boolean includeAdult, boolean includeFocus, OutputStream out) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        sites.findByUserIdAndListOrderByDomainAsc(user.getId(), SiteList.ALWAYS).forEach(s -> names.add(s.getDomain()));
        if (includeFocus) {
            sites.findByUserIdAndListOrderByDomainAsc(user.getId(), SiteList.FOCUS).forEach(s -> names.add(s.getDomain()));
        }
        if (includeAdult) {
            names.addAll(lists.adultDomains());
        }

        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write("# Anchor block list, generated " + Instant.now() + "\n");
        w.write("# Append these lines to your hosts file, or use them with your router or DNS filter.\n");
        w.write("# Windows: C:\\Windows\\System32\\drivers\\etc\\hosts   macOS/Linux: /etc/hosts\n");
        w.write("# >>> Anchor >>>\n");
        for (String d : names) {
            w.write("0.0.0.0 " + d + "\n");
            if (!d.startsWith("www.")) {
                w.write("0.0.0.0 www." + d + "\n");
            }
        }
        w.write("# <<< Anchor <<<\n");
        w.flush();
    }
}
