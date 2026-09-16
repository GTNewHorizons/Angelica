package com.gtnewhorizons.angelica.mixins.early.celeritas.frustum;

import com.gtnewhorizons.angelica.mixins.interfaces.ClippingHelperExt;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustrum;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.embeddedt.embeddium.impl.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Frustrum.class)
public abstract class MixinFrustrum implements ViewportProvider {
    @Shadow @Final private ClippingHelper clippingHelper;
    @Shadow private double xPosition, yPosition, zPosition;

    @Unique private final FrustumIntersection celeritas$snapshot = new FrustumIntersection();

    @Unique
    private void celeritas$snapshotFrustum() {
        this.celeritas$snapshot.set(((ClippingHelperExt) this.clippingHelper).celeritas$getCombinedMatrix(), false);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void celeritas$snapshotOnInit(CallbackInfo ci) {
        this.celeritas$snapshotFrustum();
    }

    @Inject(method = "setPosition", at = @At("RETURN"))
    private void celeritas$snapshotOnSetPosition(double x, double y, double z, CallbackInfo ci) {
        this.celeritas$snapshotFrustum();
    }

    @Override
    public Viewport sodium$createViewport() {
        // Shadow frustum subclasses override this via their own ViewportProvider impl.
        // This default handles vanilla Frustrum only, using the per-instance snapshot.
        return new Viewport(
            new SimpleFrustum(this.celeritas$snapshot),
            new Vector3d(this.xPosition, this.yPosition, this.zPosition)
        );
    }

    @Overwrite
    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ)
            || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        return this.celeritas$snapshot.testAab(
            (float) (minX - this.xPosition), (float) (minY - this.yPosition), (float) (minZ - this.zPosition),
            (float) (maxX - this.xPosition), (float) (maxY - this.yPosition), (float) (maxZ - this.zPosition)
        );
    }
}
