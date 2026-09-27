package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.event.ChunkBiomeDataChangedEvent;
import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;

public class CeleritasSetup {
    private static boolean initialized = false;

    public static void ensureInitialized() {
        if (!initialized) {
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
