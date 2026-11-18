package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.shadows.ShadowRenderingState;
import org.joml.Matrix4fc;

public final class FlattenedItemLighting {

    static final float FLATNESS_CUTOFF = 0.05f;

    private static boolean preLitText;

    private FlattenedItemLighting() {}

    public static void setPreLitText(boolean preLit) {
        preLitText = preLit;
    }

    static boolean isFlattened(Matrix4fc m) {
        final float ax = m.m00(), ay = m.m01(), az = m.m02();
        final float bx = m.m10(), by = m.m11(), bz = m.m12();
        final float cx = m.m20(), cy = m.m21(), cz = m.m22();
        final float bcx = by * cz - bz * cy, bcy = bz * cx - bx * cz, bcz = bx * cy - by * cx;
        final float cax = cy * az - cz * ay, cay = cz * ax - cx * az, caz = cx * ay - cy * ax;
        final float abx = ay * bz - az * by, aby = az * bx - ax * bz, abz = ax * by - ay * bx;
        final float det = ax * bcx + ay * bcy + az * bcz;
        final float frob2 = ax * ax + ay * ay + az * az + bx * bx + by * by + bz * bz + cx * cx + cy * cy + cz * cz;
        final float adj2 = bcx * bcx + bcy * bcy + bcz * bcz + cax * cax + cay * cay + caz * caz + abx * abx + aby * aby + abz * abz;
        if (!(adj2 > 0f) || det == 0f) return false;
        return Math.abs(det) * (float) Math.sqrt(frob2) < FLATNESS_CUTOFF * adj2;
    }

    public static boolean applies() {
        return GLStateManager.isMainThread() && !preLitText && TesrBatchRenderer.INSTANCE.inScope() && GLSMLighting.getLightingState().isEnabled() && !GLStateManager.isRecordingDisplayList()
            && TesrShaderPrograms.packActive() && !ShadowRenderingState.areShadowsCurrentlyBeingRendered() && isFlattened(GLSMMatrices.getModelViewMatrix());
    }
}
