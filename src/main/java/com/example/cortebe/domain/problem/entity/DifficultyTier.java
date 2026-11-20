package com.example.cortebe.domain.problem.entity;

import java.util.Arrays;
import java.util.List;

public enum DifficultyTier {
    UNRATED, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, RUBY;

    public List<Difficulty> difficulties() {
        return Arrays.stream(Difficulty.values())
            .filter(difficulty -> difficulty.name().equals(name()) || difficulty.name().startsWith(name() + "_"))
            .toList();
    }
}
