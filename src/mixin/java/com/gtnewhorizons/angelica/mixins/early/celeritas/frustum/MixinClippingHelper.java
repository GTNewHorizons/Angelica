package com.gtnewhorizons.angelica.mixins.early.celeritas.frustum;

import com.gtnewhorizons.angelica.mixins.interfaces.ClippingHelperExt;
import net.minecraft.client.renderer.culling.ClippingHelper;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClippingHelper.class)
public class MixinClippingHelper implements ClippingHelperExt {

    @Shadow public float[] projectionMatrix;
    @Shadow public float[] modelviewMatrix;

    @Unique private final FrustumIntersection celeritas$frustumIntersection = new FrustumIntersection();
    @Unique private final Matrix4f celeritas$projMatrix = new Matrix4f();
    @Unique private final Matrix4f celeritas$mvMatrix = new Matrix4f();

    @Override
    public FrustumIntersection celeritas$getFrustumIntersection() {
        return this.celeritas$frustumIntersection;
    }

    @Override
    public void celeritas$updateFrustumIntersection() {
        this.celeritas$projMatrix.set(this.projectionMatrix);
        this.celeritas$mvMatrix.set(this.modelviewMatrix);
        this.celeritas$frustumIntersection.set(this.celeritas$projMatrix.mul(this.celeritas$mvMatrix));
    }

    /**
     * @author mitchej123
     * @reason Replace vanilla frustum check with JOML
     */
    @Overwrite
    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ)
            || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        return this.celeritas$frustumIntersection.testAab(
            (float) minX, (float) minY, (float) minZ,
            (float) maxX, (float) maxY, (float) maxZ
        );
    }
}
