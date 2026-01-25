package com.gtnewhorizons.angelica.mixins.early.celeritas.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustrum;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Frustrum.class)
public abstract class MixinFrustrum implements ViewportProvider {
    @Shadow @Final private ClippingHelper clippingHelper;
    @Shadow private double xPosition, yPosition, zPosition;

    @Override
    public Viewport sodium$createViewport() {
        // Shadow frustum subclasses override this via their own ViewportProvider impl.
        // This default handles vanilla Frustrum only.
        return new Viewport(
            this.clippingHelper::isBoxInFrustum,
            new Vector3d(this.xPosition, this.yPosition, this.zPosition)
        );
    }
}
