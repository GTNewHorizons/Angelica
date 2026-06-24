package com.gtnewhorizons.angelica.compat.etfuturum;

import cpw.mods.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Reflective bridge to Et Futurum Requiem's End flash state. Replace if PR is ever accepted.
 */
public final class EndFlashCompat {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/EndFlashCompat");

    private static final boolean AVAILABLE;
    private static final Method GET_INTENSITY;
    private static final Method GET_X_ANGLE;
    private static final Method GET_Y_ANGLE;

    static {
        boolean available = false;
        Method getIntensity = null;
        Method getXAngle = null;
        Method getYAngle = null;
        if (Loader.isModLoaded("etfuturum")) {
            try {
                final Class<?> handler = Class.forName(
                    "ganymedes01.etfuturum.core.handlers.client.ClientEventHandler",
                    false, EndFlashCompat.class.getClassLoader());
                getIntensity = handler.getMethod("getEndFlashIntensity", float.class);
                getXAngle = handler.getMethod("getEndFlashXAngle");
                getYAngle = handler.getMethod("getEndFlashYAngle");
                available = true;
            } catch (Throwable t) {
                LOGGER.info("Et Futurum Requiem is present but does not expose the End flash API; the endFlashIntensity uniform will read 0.");
            }
        }
        AVAILABLE = available;
        GET_INTENSITY = getIntensity;
        GET_X_ANGLE = getXAngle;
        GET_Y_ANGLE = getYAngle;
    }

    private EndFlashCompat() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static float getIntensity(float partialTicks) {
        if (!AVAILABLE) return 0.0F;
        try {
            return (Float) GET_INTENSITY.invoke(null, partialTicks);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    public static float getXAngle() {
        if (!AVAILABLE) return 0.0F;
        try {
            return (Float) GET_X_ANGLE.invoke(null);
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    public static float getYAngle() {
        if (!AVAILABLE) return 0.0F;
        try {
            return (Float) GET_Y_ANGLE.invoke(null);
        } catch (Throwable t) {
            return 0.0F;
        }
    }
}
