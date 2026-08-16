package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class FFPVertexLighting {

    private static final Vector3f scratchNormal = new Vector3f();
    private static final Vector3f scratchLightVec = new Vector3f();
    private static final Vector4f scratchEyePos = new Vector4f();
    private static final Matrix3f scratchNormalMat = new Matrix3f();
    private static final Vector3f scratchVec = new Vector3f();

    private FFPVertexLighting() {}

    public static boolean modulatesVertexColor(Vector3f destFactor) {
        destFactor.set(1.0f);

        if (!GLStateManager.getLightingState().isEnabled()) return false;
        if (!GLStateManager.getColorMaterial().isEnabled()) return false;
        if (VertexKey.encodeColorMaterialMode(GLStateManager.getColorMaterialParameter().getValue()) != VertexKey.CM_AMBIENT_AND_DIFFUSE) return false;

        final MaterialState material = GLStateManager.getFrontMaterial();
        if (material.emission.x != 0.0f || material.emission.y != 0.0f || material.emission.z != 0.0f)
            return false;

        final boolean materialHasSpecular = material.shininess > 0.0f && (material.specular.x != 0.0f || material.specular.y != 0.0f || material.specular.z != 0.0f);

        final Vector4f lightModelAmbient = GLStateManager.getLightModel().ambient;
        float r = lightModelAmbient.x;
        float g = lightModelAmbient.y;
        float b = lightModelAmbient.z;

        final var lightEnables = GLStateManager.getLightStates();
        final var lightData = GLStateManager.getLightDataStates();
        boolean normalReady = false;

        for (int i = 0; i < VertexKey.FFP_LIGHT_COUNT; i++) {
            if (!lightEnables[i].isEnabled()) continue;
            final LightState light = lightData[i];
            if (materialHasSpecular && (light.specular.x != 0.0f || light.specular.y != 0.0f || light.specular.z != 0.0f))
                return false;
            if (!normalReady) {
                transformCurrentNormal();
                normalReady = true;
            }
            final float nDotVP = Math.max(scratchNormal.dot(lightDirection(light)), 0.0f);
            r += light.ambient.x + nDotVP * light.diffuse.x;
            g += light.ambient.y + nDotVP * light.diffuse.y;
            b += light.ambient.z + nDotVP * light.diffuse.z;
        }

        if (r == 1.0f && g == 1.0f && b == 1.0f) return false;
        if (!Float.isFinite(r + g + b)) return false;
        destFactor.set(r, g, b);
        return true;
    }

    private static void transformCurrentNormal() {
        GLStateManager.getModelViewMatrix().normal(scratchNormalMat);
        scratchNormal.set(ShaderManager.getCurrentNormal());
        scratchNormalMat.transform(scratchNormal);
        if (GLStateManager.getNormalizeState().isEnabled()) {
            scratchNormal.normalize();
        } else if (GLStateManager.getRescaleNormalState().isEnabled()) {
            scratchNormal.mul(Uniforms.rescaleFactor(scratchNormalMat, scratchVec));
        }
    }

    private static Vector3f lightDirection(LightState light) {
        if (light.position.w == 0.0f) {
            scratchLightVec.set(light.position.x, light.position.y, light.position.z);
        } else {
            scratchEyePos.set(0.0f, 0.0f, 0.0f, 1.0f).mul(GLStateManager.getModelViewMatrix());
            scratchLightVec.set(light.position.x - scratchEyePos.x, light.position.y - scratchEyePos.y, light.position.z - scratchEyePos.z);
        }
        return scratchLightVec.normalize();
    }
}
