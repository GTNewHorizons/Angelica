package com.gtnewhorizons.angelica.client.gui.options.named;

import net.minecraft.util.MathHelper;

public enum ParticleMode implements NamedState {
    ALL("options.particles.all"),
    DECREASED("options.particles.decreased"),
    MINIMAL("options.particles.minimal");

    private static final ParticleMode[] VALUES = values();

    private final String name;

    ParticleMode(String name) {
        this.name = name;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public static ParticleMode fromOrdinal(int ordinal) {
        return VALUES[MathHelper.clamp_int(ordinal, 0, VALUES.length - 1)];
    }
}
