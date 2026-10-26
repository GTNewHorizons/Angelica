package com.gtnewhorizons.angelica.api.debug;

public final class DebugEntrySpec {

    public interface DebugEntryFactory {

        DebugScreenEntry create(DebugEntryHandle entry);
    }

    public interface DebugToggleListener {

        void onEnabledChanged(DebugEntryHandle entry, boolean enabled);
    }

    private final String id;
    private final DebugEntryCategory category;
    private String name;
    private DebugScreenEntryStatus defaultStatus;
    private DebugScreenEntryStatus performanceStatus = DebugScreenEntryStatus.NEVER;
    private DebugEntryFactory textFactory;
    private DebugToggleListener toggleListener;
    private boolean supportsAlways = true;

    public DebugEntrySpec(String id, DebugEntryCategory category) {
        this.id = id;
        this.category = category;
        this.defaultStatus = category == DebugEntryCategory.SCREEN_TEXT ? DebugScreenEntryStatus.IN_OVERLAY : DebugScreenEntryStatus.NEVER;
    }

    public DebugEntrySpec name(String keyOrLiteral) {
        this.name = keyOrLiteral;
        return this;
    }

    public DebugEntrySpec defaults(DebugScreenEntryStatus defaultStatus, DebugScreenEntryStatus performanceStatus) {
        this.defaultStatus = defaultStatus;
        this.performanceStatus = performanceStatus;
        return this;
    }

    public DebugEntrySpec text(DebugEntryFactory factory) {
        this.textFactory = factory;
        return this;
    }

    public DebugEntrySpec toggle(DebugToggleListener listener) {
        this.toggleListener = listener;
        return this;
    }

    public DebugEntrySpec supportsAlways(boolean supportsAlways) {
        this.supportsAlways = supportsAlways;
        return this;
    }

    public String getId() {
        return id;
    }

    public DebugEntryCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public DebugScreenEntryStatus getDefaultStatus() {
        return defaultStatus;
    }

    public DebugScreenEntryStatus getPerformanceStatus() {
        return performanceStatus;
    }

    public DebugEntryFactory getTextFactory() {
        return textFactory;
    }

    public DebugToggleListener getToggleListener() {
        return toggleListener;
    }

    public boolean getSupportsAlways() {
        return supportsAlways;
    }
}
