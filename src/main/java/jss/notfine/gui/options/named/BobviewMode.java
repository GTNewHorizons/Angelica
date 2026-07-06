package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum BobviewMode implements NamedState {
    DEFAULT("generator.default"),
    HAND("options.bobview.hand"),
    CAMERA("options.bobview.camera");

    private final String name;

    BobviewMode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }
}
