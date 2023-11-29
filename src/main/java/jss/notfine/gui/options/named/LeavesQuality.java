package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum LeavesQuality implements NamedState {
    DEFAULT("generator.default"),
    FANCY("options.graphics.fancy"),
    FAST("options.graphics.fast"),
    SMART("options.graphics.smart"),
    SHELLED_FANCY("options.graphics.shelledfancy"),
    SHELLED_FAST("options.graphics.shelledfast");

    private final String name;

    LeavesQuality(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}
