package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;

public final class ParticleCulling {

    private ParticleCulling() {}

    public static boolean visible(CeleritasWorldRenderer renderer, double x, double y, double z) {
        return renderer == null || renderer.isPointVisible(x, y, z);
    }
}
