package com.gtnewhorizons.angelica.glsm.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public enum VSyncMode {
    AUTO,
    ON,
    MAILBOX,
    OFF;

    private static final List<VSyncMode> OFF_ORDER = List.of(OFF, MAILBOX, ON);
    private static final List<VSyncMode> MAILBOX_ORDER = List.of(MAILBOX, ON);
    private static final List<VSyncMode> ON_ORDER = List.of(ON);

    private static final List<VSyncMode> DISPLAY_ORDER = List.of(OFF, ON, MAILBOX);

    public boolean blocksOnVBlank() {
        return this == ON;
    }

    public boolean usesVSyncSwapInterval() {
        return this == ON || this == MAILBOX;
    }

    public VSyncMode swapIntervalEquivalent() {
        return this == MAILBOX ? ON : this;
    }

    public VSyncMode resolve(boolean vsyncEnabled) {
        return this != AUTO ? this : (vsyncEnabled ? ON : OFF);
    }

    public List<VSyncMode> fallbackOrder() {
        return switch (this) {
            case OFF -> OFF_ORDER;
            case MAILBOX -> MAILBOX_ORDER;
            default -> ON_ORDER;
        };
    }

    public boolean tearFree() {
        return this != OFF;
    }

    public static List<VSyncMode> selectable(Predicate<VSyncMode> supported) {
        final List<VSyncMode> modes = new ArrayList<>(DISPLAY_ORDER.size());
        for (VSyncMode mode : DISPLAY_ORDER) {
            if (supported.test(mode)) modes.add(mode);
        }
        return modes;
    }
}
