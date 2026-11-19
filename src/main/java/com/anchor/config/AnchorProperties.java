package com.anchor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed access to the {@code anchor.*} settings in application.yml. */
@ConfigurationProperties(prefix = "anchor")
public record AnchorProperties(String rememberMeKey, Blocklist blocklist) {

    public record Blocklist(String updateUrl) {
    }
}
