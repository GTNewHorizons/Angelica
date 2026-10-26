package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec.DebugEntryFactory;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec.DebugToggleListener;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;

public final class DebugEntryDef implements DebugEntryHandle {

    public final String id;
    public final String namespace;
    public final String path;
    public final String name;
    public final DebugEntryCategory category;
    public final DebugScreenEntryStatus defaultStatus;
    public final DebugScreenEntryStatus performanceStatus;
    public final int index;
    public final int kind;
    public final int rendererIndex;
    public final boolean supportsAlways;
    public final boolean flat;

    final DebugRegistry registry;
    final DebugEntryFactory textFactory;
    final DebugToggleListener toggleListener;

    boolean enabled;
    boolean alwaysOn;
    boolean failed;
    boolean sampleOffered;
    private DebugScreenEntry entry;

    DebugEntryDef(DebugRegistry registry, DebugEntrySpec spec, int index, int kind, int rendererIndex, boolean flat) {
        this.registry = registry;
        this.id = spec.getId();
        final int colon = id.indexOf(':');
        this.namespace = id.substring(0, colon);
        this.path = id.substring(colon + 1);
        this.name = spec.getName();
        this.category = spec.getCategory();
        this.defaultStatus = spec.getDefaultStatus();
        this.performanceStatus = spec.getPerformanceStatus();
        this.index = index;
        this.kind = kind;
        this.rendererIndex = rendererIndex;
        this.supportsAlways = spec.getSupportsAlways();
        this.flat = flat;
        this.textFactory = spec.getTextFactory();
        this.toggleListener = spec.getToggleListener();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAlwaysOn() {
        return alwaysOn;
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    public boolean hasText() {
        return textFactory != null;
    }

    public boolean isScreenText() {
        return category == DebugEntryCategory.SCREEN_TEXT && hasText();
    }

    public DebugScreenEntryStatus profileStatus(DebugScreenProfile profile) {
        return profile == DebugScreenProfile.PERFORMANCE ? performanceStatus : defaultStatus;
    }

    public DebugScreenEntry entryOrCreate() {
        DebugScreenEntry created = entry;
        if (created == null && textFactory != null) {
            created = textFactory.create(this);
            entry = created;
        }
        return created;
    }
}
