package org.embeddedt.archaicfix.occlusion;

import net.minecraft.client.renderer.WorldRenderer;

public interface IRenderGlobalListener {

    /** Called when a world renderer changes while it's already dirty. */
    void onDirtyRendererChanged(WorldRenderer wr);

}
