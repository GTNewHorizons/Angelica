package jss.notfine.gui.options.named;

import com.gtnewhorizons.angelica.client.gui.options.named.NamedState;

public enum DownfallQuality implements NamedState {
    DEFAULT("generator.default"),
    FANCY("options.graphics.fancy"),
    FAST("options.graphics.fast"),
    ULTRA("options.graphics.ultra"),
    OFF("options.off");

    private final String name;

    DownfallQuality(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}
