package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.lwjgl.opengl.GL15;
import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

public class CeleritasSetup {
    private static boolean initialized = false;

    public static void ensureInitialized() {
        if (!initialized && AngelicaConfig.enableCeleritas) {
            GLRenderDevice.VANILLA_STATE_RESETTER = () -> GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            LOGGER.debug("Celeritas init");
            initialized = true;
        }
    }
}
