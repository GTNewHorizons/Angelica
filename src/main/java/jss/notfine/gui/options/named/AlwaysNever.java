package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;

public enum AlwaysNever implements NamedState {
    DEFAULT("generator.default"),
    ALWAYS("options.stream.chat.enabled.always"),
    NEVER("options.stream.chat.enabled.never");

    private final String name;

    AlwaysNever(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}
