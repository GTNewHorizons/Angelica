package com.gtnewhorizons.umbra.loading.shared;

import net.minecraft.launchwrapper.Launch;

/**
 * Detects whether Angelica is present. If so, Umbra should disable itself entirely since Angelica provides its own GLSM integration.
 */
public final class AngelicaDetector {

    private static Boolean result;

    public static boolean isPresent() {
        if (result != null) return result;

        boolean found = AngelicaDetector.class.getClassLoader().getResource("com/gtnewhorizons/angelica/loading/AngelicaTweaker.class") != null;

        if (!found && Launch.blackboard != null) {
            found = Boolean.TRUE.equals(Launch.blackboard.get("angelica.rfbPluginLoaded"));
        }

        result = found;
        return found;
    }

    private AngelicaDetector() {}
}
