package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.proxy.ClientProxy;

public final class GuiBlockSupersampling {
    private GuiBlockSupersampling() {}

    public static int begin() {
        if (!RenderingState.INSTANCE.isRenderingGuiItem() || RenderingState.INSTANCE.isRenderingWorld()) return 0;
        final int samples = ClientProxy.options().quality.guiBlockSupersampling;
        return samples > 1 ? GLSMTextures.setTextureSupersamplingSamples(samples) : 0;
    }

    public static void end(int previous) {
        if (previous != 0) GLSMTextures.setTextureSupersamplingSamples(previous);
    }
}
