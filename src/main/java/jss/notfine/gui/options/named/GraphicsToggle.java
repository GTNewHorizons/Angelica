package jss.notfine.gui.options.named;

import com.gtnewhorizons.angelica.client.gui.options.named.NamedState;

public enum GraphicsToggle implements NamedState {
    DEFAULT("generator.default"),
    ON("options.on"),
    OFF("options.off");

    private final String name;

    GraphicsToggle(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

}
