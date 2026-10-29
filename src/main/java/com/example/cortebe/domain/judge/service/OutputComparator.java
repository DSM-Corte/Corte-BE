package com.example.cortebe.domain.judge.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String expected, String actual) {
        return matches(expected, actual.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean matches(String expected, byte[] actual) {
        return Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), actual);
    }
}
