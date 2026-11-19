package com.anchor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Anchor — attention, protected.
 * <p>
 * A focus and digital-wellbeing web app: plan around your energy, make focus a commitment,
 * ride out urges, and keep a clean streak.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AnchorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnchorApplication.class, args);
    }
}
