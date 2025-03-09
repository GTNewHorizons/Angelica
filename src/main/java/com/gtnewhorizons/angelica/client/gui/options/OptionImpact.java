package com.gtnewhorizons.angelica.client.gui.options;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

public enum OptionImpact {
    LOW(EnumChatFormatting.GREEN, I18n.format("sodium.option_impact.low")),
    MEDIUM(EnumChatFormatting.YELLOW, I18n.format("sodium.option_impact.medium")),
    HIGH(EnumChatFormatting.GOLD, I18n.format("sodium.option_impact.high")),
    EXTREME(EnumChatFormatting.RED, I18n.format("sodium.option_impact.extreme")),
    VARIES(EnumChatFormatting.WHITE, I18n.format("sodium.option_impact.varies"));

    private final EnumChatFormatting color;
    private final String text;

    OptionImpact(EnumChatFormatting color, String text) {
        this.color = color;
        this.text = text;
    }

    public String toDisplayString() {
        return this.color + this.text;
    }
}
