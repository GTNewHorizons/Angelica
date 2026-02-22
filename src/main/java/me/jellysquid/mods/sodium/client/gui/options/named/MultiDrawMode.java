package me.jellysquid.mods.sodium.client.gui.options.named;

public enum MultiDrawMode implements NamedState {
    DIRECT("sodium.options.multidraw_mode.direct"),
    INDIRECT("sodium.options.multidraw_mode.indirect"),
    INDIVIDUAL("sodium.options.multidraw_mode.individual");

    private final String key;

    MultiDrawMode(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return this.key;
    }
}
