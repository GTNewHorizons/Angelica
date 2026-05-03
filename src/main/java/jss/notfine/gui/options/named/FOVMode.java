package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum FOVMode implements NamedState {
    ALL("options.fov.all"),
    MODS("options.fov.mods"),
    NONE("options.fov.none");

    private final String name;

    FOVMode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}
