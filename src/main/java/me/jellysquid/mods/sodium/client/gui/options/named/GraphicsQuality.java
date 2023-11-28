package me.jellysquid.mods.sodium.client.gui.options.named;

import net.minecraft.client.Minecraft;

public enum GraphicsQuality implements NamedState {
    DEFAULT("generator.default"),
    FANCY("options.graphics.fancy"),
    FAST("options.graphics.fast");

    private final String name;

    GraphicsQuality(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public boolean isFancy() {
        return this == FANCY || (this == DEFAULT && Minecraft.getMinecraft().gameSettings.fancyGraphics);
    }

}

