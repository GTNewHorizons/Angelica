package me.jellysquid.mods.sodium.client.gui.options;

import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

public enum OptionImpact {
    LOW(Formatting.GREEN, new TranslatableText("sodium.option_impact.low").getString()),
    MEDIUM(Formatting.YELLOW, new TranslatableText("sodium.option_impact.medium").getString()),
    HIGH(Formatting.GOLD, new TranslatableText("sodium.option_impact.high").getString()),
    EXTREME(Formatting.RED, new TranslatableText("sodium.option_impact.extreme").getString()),
    VARIES(Formatting.WHITE, new TranslatableText("sodium.option_impact.varies").getString());

    private final Formatting color;
    private final String text;

    OptionImpact(Formatting color, String text) {
        this.color = color;
        this.text = text;
    }

    public String toDisplayString() {
        return this.color + this.text;
    }
}
