package com.gtnewhorizons.angelica.compat.lotr;

import net.minecraft.world.WorldProvider;

public final class LOTRCompat {

    private static final Class<?> LOTR_WORLD_PROVIDER_CLASS;

    static {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("lotr.common.world.LOTRWorldProvider", false, LOTRCompat.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {}
        LOTR_WORLD_PROVIDER_CLASS = clazz;
    }

    private LOTRCompat() {}

    public static boolean isLotrProvider(WorldProvider provider) {
        return LOTR_WORLD_PROVIDER_CLASS != null && LOTR_WORLD_PROVIDER_CLASS.isInstance(provider);
    }
}
