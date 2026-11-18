package com.gtnewhorizons.angelica.experimental.surround.targets;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public final class Trace {

    private static final List<String> EVENTS = new ArrayList<>();
    private static long counter;

    private Trace() {
    }

    public static void reset() {
        EVENTS.clear();
        counter = 0;
    }

    public static void add(String event) {
        EVENTS.add(event);
    }

    public static void assertEvents(String... expected) {
        assertArrayEquals(expected, EVENTS.toArray(new String[EVENTS.size()]), EVENTS.toString());
    }

    public static long nextToken() {
        return ++counter;
    }
}
