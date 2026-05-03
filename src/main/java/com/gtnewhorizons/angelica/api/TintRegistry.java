package com.gtnewhorizons.angelica.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for tint blend modes used by the colored light pipeline. Provides a default no-tint mode.
 * External mods (e.g. Supernova) can register additional modes via {@link #registerMode(String, TintComputer)}.
 *
 */
public final class TintRegistry {

    private static final TintComputer NO_TINT = (br, bg, bb, sr, sg, sb, out) -> {
        out[0] = 1f;
        out[1] = 1f;
        out[2] = 1f;
    };

    private static final List<TintComputer> computers = new ArrayList<>();
    private static final List<String> names = new ArrayList<>();
    private static TintComputer current;
    private static volatile int currentOrdinal;

    static {
        registerMode("NONE", NO_TINT);
        current = NO_TINT;
        currentOrdinal = 0;
    }

    private TintRegistry() {}

    /** Register a named tint blend mode. Returns the ordinal assigned. */
    public static int registerMode(String name, TintComputer computer) {
        final int ordinal = computers.size();
        computers.add(computer);
        names.add(name);
        return ordinal;
    }

    /** Returns the currently active tint computer. */
    public static TintComputer getCurrent() {
        return current;
    }

    /** Returns the ordinal of the current mode, or -1 if a custom (non-registered) computer is active. */
    public static int getCurrentOrdinal() {
        return currentOrdinal;
    }

    /** Switch to a registered mode by ordinal. */
    public static void setCurrentByOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= computers.size()) {
            throw new IllegalArgumentException("Invalid tint mode ordinal: " + ordinal + " (registered modes: " + computers.size() + ")");
        }
        current = computers.get(ordinal);
        currentOrdinal = ordinal;
    }

    /** Set a custom tint computer directly. */
    public static void setCurrent(TintComputer computer) {
        current = computer;
        // Resolve ordinal; custom computers not in the registry get -1
        for (int i = 0; i < computers.size(); i++) {
            if (computers.get(i) == computer) {
                currentOrdinal = i;
                return;
            }
        }
        currentOrdinal = -1;
    }

    /** Returns the number of registered blend modes. */
    public static int getModeCount() {
        return computers.size();
    }

    /** Returns the display name of a registered mode by ordinal. */
    public static String getModeName(int ordinal) {
        if (ordinal < 0 || ordinal >= names.size()) return "UNKNOWN";
        return names.get(ordinal);
    }
}
