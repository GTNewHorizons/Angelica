package com.gtnewhorizons.angelica.dynamiclights.config;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;

public final class EntityTypeEntry {

    private final String displayName;
    private final Class<?> entityClass;
    private final String modId;
    private final String searchName;
    private final String searchModId;

    public EntityTypeEntry(@NotNull String displayName, @NotNull Class<?> entityClass, @NotNull String modId) {
        this.displayName = displayName;
        this.entityClass = entityClass;
        this.modId = modId;
        this.searchName = displayName.toLowerCase(Locale.ROOT);
        this.searchModId = modId.toLowerCase(Locale.ROOT);
    }

    public String getDisplayName() {
        return displayName;
    }

    public Class<?> getEntityClass() {
        return entityClass;
    }

    public String getModId() {
        return modId;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getSearchModId() {
        return searchModId;
    }

    @Override
    public String toString() {
        return displayName + " (" + modId + ")";
    }
}
