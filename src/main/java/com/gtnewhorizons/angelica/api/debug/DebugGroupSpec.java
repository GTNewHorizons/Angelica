package com.gtnewhorizons.angelica.api.debug;

public final class DebugGroupSpec {

    public static final int DEFAULT_ORDER = 500;

    private final String id;
    private String titleKey;
    private int accent = -1;
    private int side = DebugGroupHandle.LEFT;
    private int order = DEFAULT_ORDER;

    public DebugGroupSpec(String id) {
        this.id = id;
    }

    public DebugGroupSpec title(String keyOrLiteral) {
        this.titleKey = keyOrLiteral;
        return this;
    }

    public DebugGroupSpec accent(int rgb) {
        this.accent = rgb & 0xFFFFFF;
        return this;
    }

    public DebugGroupSpec side(int side) {
        this.side = side;
        return this;
    }

    public DebugGroupSpec order(int order) {
        this.order = order;
        return this;
    }

    public String getId() {
        return id;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public int getAccent() {
        return accent;
    }

    public int getSide() {
        return side;
    }

    public int getOrder() {
        return order;
    }
}
