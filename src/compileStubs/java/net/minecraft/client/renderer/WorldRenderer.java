package net.minecraft.client.renderer;

import net.minecraft.client.renderer.culling.ICamera;

public class WorldRenderer {

    public boolean isInFrustum;

    public boolean skipAllRenderPasses() {
        return false;
    }

    public void updateInFrustum(ICamera frustrum) {}
}
