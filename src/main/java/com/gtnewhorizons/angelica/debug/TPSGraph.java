package com.gtnewhorizons.angelica.debug;

import net.minecraft.util.ResourceLocation;

public class TPSGraph extends F3Graph {
    public TPSGraph() {
        // At 1x scale, 20 TPS should be 60 px. 20 FPS = 50_000_000ns per frame, 60px/50_000_000ns = 0.0000012 px/ns
        super(
            new ResourceLocation("angelica:textures/tps_fg.png"),
            0.0000012f,
            false
        );
    }
}
