package com.gtnewhorizons.angelica.debug;

import net.minecraft.util.ResourceLocation;

public class FrametimeGraph extends F3Graph {
    public FrametimeGraph() {
        // At 1x scale, 30 FPS should be 60 px. 30 FPS = 33_333_333ns per frame, 60px/33_333_333ns = 0.0000018f px/ns
        super(
            new ResourceLocation("angelica:textures/frametimes_fg.png"),
            0.0000018f,
            true
        );
    }
}
