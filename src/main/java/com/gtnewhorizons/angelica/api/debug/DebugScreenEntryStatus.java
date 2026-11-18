package com.gtnewhorizons.angelica.api.debug;

public enum DebugScreenEntryStatus {

    ALWAYS_ON("alwaysOn"),
    IN_OVERLAY("inOverlay"),
    NEVER("never");

    private static final DebugScreenEntryStatus[] VALUES = values();

    public final String serializedName;

    DebugScreenEntryStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public static DebugScreenEntryStatus bySerializedName(String name) {
        for (final DebugScreenEntryStatus status : VALUES) {
            if (status.serializedName.equals(name)) return status;
        }
        return null;
    }
}
