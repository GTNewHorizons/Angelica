package com.gtnewhorizons.angelica.client.gui.options.named;

public enum GraphicsMode implements NamedState {
    FANCY("options.graphics.fancy"),
    FAST("options.graphics.fast");

    private final String name;

    GraphicsMode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public boolean isFancy() {
        return this == FANCY;
    }

    public static GraphicsMode fromBoolean(boolean isFancy) {
        return isFancy ? FANCY : FAST;
    }

}
