package me.jellysquid.mods.sodium.client.gui.options.named;

public enum TranslucencyMode implements NamedState {
    OFF("options.off"),
    FAST("options.graphics.fast"),
    FANCY("options.graphics.fancy");

    private final String name;

    TranslucencyMode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public boolean sorts() {
        return this != OFF;
    }

    public boolean composites() {
        return this == FANCY;
    }
}
