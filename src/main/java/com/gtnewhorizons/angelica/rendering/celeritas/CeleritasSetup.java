package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.event.ChunkBiomeDataChangedEvent;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import org.embeddedt.embeddium.impl.gl.device.GLRenderDevice;
import org.lwjgl.opengl.GL15;

public class CeleritasSetup {
    private static boolean initialized = false;

    public static void ensureInitialized() {
        if (!initialized && AngelicaConfig.enableCeleritas) {
            GLRenderDevice.VANILLA_STATE_RESETTER = () -> GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            ChunkBiomeDataChangedEvent.BUS.addListener(CeleritasSetup::onChunkBiomeDataChanged);
            AngelicaClientTweaker.LOGGER.debug("Celeritas init");
            initialized = true;
        }
    }

    private static void onChunkBiomeDataChanged(ChunkBiomeDataChangedEvent event) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer != null && renderer.isActive()) {
            renderer.getRenderSectionManager().onBiomesChanged(event.chunkX, event.chunkZ);
        }
    }
}
