package com.anchor.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.anchor.config.AnchorProperties;
import com.anchor.util.Domains;

/**
 * The community-maintained adult-content list (StevenBlack/hosts "porn-only"). A compressed copy
 * ships with the app; an administrator can refresh it from the internet.
 */
@Service
public class BlockListService {

    private static final Logger log = LoggerFactory.getLogger(BlockListService.class);
    private static final String BUNDLED = "blocklists/adult_domains.txt.gz";
    private static final Path UPDATED = Path.of("data", "adult_domains.txt");

    private final AnchorProperties properties;
    private volatile Set<String> adult;
    private volatile Instant updatedAt;

    public BlockListService(AnchorProperties properties) {
        this.properties = properties;
    }

    public Set<String> adultDomains() {
        Set<String> current = adult;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (adult == null) {
                adult = Collections.unmodifiableSet(load());
            }
            return adult;
        }
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private Set<String> load() {
        Set<String> set = new LinkedHashSet<>();
        try {
            if (Files.exists(UPDATED)) {
                for (String line : Files.readAllLines(UPDATED, StandardCharsets.UTF_8)) {
                    addIfValid(set, line);
                }
                updatedAt = Files.getLastModifiedTime(UPDATED).toInstant();
            }
            if (set.size() < 1000) {
                set.clear();
                updatedAt = null;
                try (InputStream raw = new ClassPathResource(BUNDLED).getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        addIfValid(set, line);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Could not load the adult blocklist", e);
        }
        log.info("Adult blocklist loaded: {} domains", set.size());
        return set;
    }

    /** Downloads the latest list. Returns the number of domains. */
    public synchronized int update() throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.blocklist().updateUrl()))
                .timeout(Duration.ofSeconds(60)).GET().build();
        String body = http.send(request, HttpResponse.BodyHandlers.ofString()).body();

        Set<String> set = new LinkedHashSet<>();
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith("0.0.0.0 ")) {
                continue;
            }
            String domain = line.substring(8).split("#")[0].trim();
            if (!domain.equals("0.0.0.0")) {
                addIfValid(set, domain);
            }
        }
        if (set.size() < 1000) {
            throw new IOException("The downloaded list looks incomplete; keeping the current list.");
        }

        Files.createDirectories(UPDATED.getParent());
        Files.write(UPDATED, set, StandardCharsets.UTF_8);
        adult = Collections.unmodifiableSet(set);
        updatedAt = Instant.now();
        return set.size();
    }

    private static void addIfValid(Set<String> set, String line) {
        String d = line.trim().toLowerCase(Locale.ROOT);
        if (d.length() < 3 || d.startsWith("#") || !d.contains(".") || Domains.isSafeListed(d)) {
            return;
        }
        set.add(d);
    }
}
