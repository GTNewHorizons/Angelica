package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.proxy.ClientProxy;

public final class GuiBlockSupersampling {
    private GuiBlockSupersampling() {}

    public static int begin() {
        if (!RenderingState.INSTANCE.isRenderingGuiItem() || RenderingState.INSTANCE.isRenderingWorld()) return 0;
        final int samples = ClientProxy.options().quality.guiBlockSupersampling;
        return samples > 1 ? GLStateManager.setTextureSupersamplingSamples(samples) : 0;
    }

    public static void end(int previous) {
        if (previous != 0) GLStateManager.setTextureSupersamplingSamples(previous);
    }
}
