package com.gtnewhorizons.angelica.client.gui.options.named;

import net.minecraft.util.MathHelper;

public enum LightingQuality implements NamedState {
    OFF("options.ao.off"),
    LOW("options.ao.min"),
    HIGH("options.ao.max");

    private static final LightingQuality[] VALUES = values();

    private final String name;

    private final int vanilla;

    LightingQuality(String name) {
        this.name = name;
        this.vanilla = ordinal();
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public int getVanilla() {
        return vanilla;
    }

    public static LightingQuality fromOrdinal(int ordinal) {
        return VALUES[MathHelper.clamp_int(ordinal, 0, VALUES.length - 1)];
    }
}
