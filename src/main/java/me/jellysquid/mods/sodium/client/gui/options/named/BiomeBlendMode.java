package me.jellysquid.mods.sodium.client.gui.options.named;

public enum BiomeBlendMode implements NamedState {
    FAST("options.graphics.fast"),
    FANCY("options.graphics.fancy");

    private final String key;

    BiomeBlendMode(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
