package com.gtnewhorizons.angelica.debug.screen;

public enum DebugScreenProfile {

    DEFAULT("default", "angelica.debug.options.profile.default"),
    PERFORMANCE("performance", "angelica.debug.options.profile.performance");

    public static final DebugScreenProfile[] VALUES = values();

    public final String serializedName;
    public final String translationKey;

    DebugScreenProfile(String serializedName, String translationKey) {
        this.serializedName = serializedName;
        this.translationKey = translationKey;
    }

    public static DebugScreenProfile bySerializedName(String name) {
        for (final DebugScreenProfile profile : VALUES) {
            if (profile.serializedName.equals(name)) return profile;
        }
        return null;
    }
}
