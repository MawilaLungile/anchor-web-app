package com.anchor.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anchor.domain.AppUser;
import com.anchor.domain.EnergyEntry;
import com.anchor.repo.EnergyRepository;

@Service
public class EnergyService {

    /** The five check-in levels. */
    public record Level(int value, String name, String description, String icon, String color) {
    }

    public static final List<Level> LEVELS = List.of(
            new Level(1, "Drained", "Running on empty", "battery_1_bar", "#E5604D"),
            new Level(2, "Low", "Sluggish, easily distracted", "battery_2_bar", "#F2994A"),
            new Level(3, "Steady", "Fine for routine work", "battery_3_bar", "#E2B93B"),
            new Level(4, "Good", "Clear and motivated", "battery_5_bar", "#4DBB7F"),
            new Level(5, "Peak", "Sharp, ready for deep work", "bolt", "#2E9E6B"));

    private final EnergyRepository energy;
    private final UserService users;

    public EnergyService(EnergyRepository energy, UserService users) {
        this.energy = energy;
        this.users = users;
    }

    public static Level level(int value) {
        return LEVELS.get(Math.max(1, Math.min(5, value)) - 1);
    }

    @Transactional
    public void record(AppUser user, int level) {
        EnergyEntry e = new EnergyEntry();
        e.setUserId(user.getId());
        e.setLevel(Math.max(1, Math.min(5, level)));
        e.setRecordedAt(users.clockFor(user).now());
        energy.save(e);
    }

    public List<EnergyEntry> since(AppUser user, Instant from) {
        return energy.findByUserIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(user.getId(), from);
    }
}
