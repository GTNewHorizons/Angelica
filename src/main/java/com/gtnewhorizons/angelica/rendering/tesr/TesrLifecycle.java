package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.rendering.particles.ParticleInstancer;

public final class TesrLifecycle {

    private TesrLifecycle() {}

    public static void reset() {
        TesrBatchRenderer.INSTANCE.clearRetained();
        ModelPartBatcher.INSTANCE.clear();
        ParticleInstancer.clear();

        RenderLayer.clearInterningAndHooks();
        DrawState.clearInterning();
    }
}
