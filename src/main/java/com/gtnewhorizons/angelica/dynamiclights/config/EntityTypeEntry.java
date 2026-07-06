package com.gtnewhorizons.angelica.dynamiclights.config;

import org.jetbrains.annotations.NotNull;

public class EntityTypeEntry {
    private final String displayName;
    private final String className;
    private final String modId;
    private boolean enabled;

    public EntityTypeEntry(@NotNull String displayName, @NotNull String className, @NotNull String modId, boolean enabled) {
        this.displayName = displayName;
        this.className = className;
        this.modId = modId;
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getClassName() {
        return className;
    }

    public String getModId() {
        return modId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return displayName + " (" + modId + ") [" + (enabled ? "ON" : "OFF") + "]";
    }
}
