package com.gtnewhorizons.angelica.compat.etfuturum;

import cpw.mods.fml.common.Loader;
import ganymedes01.etfuturum.api.client.EndFlashAPI;

/**
 * Use EFR to power End flash uniforms.
 */
public final class EndFlashCompat {

    private static final boolean AVAILABLE = Loader.isModLoaded("etfuturum") && EndFlashCompat.class.getResource("/ganymedes01/etfuturum/api/client/EndFlashAPI.class") != null;

    private EndFlashCompat() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static float getIntensity(float partialTicks) {
        return AVAILABLE ? EndFlashAPI.getIntensity(partialTicks) : 0.0F;
    }

    public static float getXAngle() {
        return AVAILABLE ? EndFlashAPI.getXAngle() : 0.0F;
    }

    public static float getYAngle() {
        return AVAILABLE ? EndFlashAPI.getYAngle() : 0.0F;
    }
}
