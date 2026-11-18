package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
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
    private static final Vector3f scratchFactor = new Vector3f();
    private static final Vector3f base = new Vector3f();
    private static final Vector3f[] lightDirs = new Vector3f[VertexKey.FFP_LIGHT_COUNT];
    private static final Vector3f[] lightDiffuse = new Vector3f[VertexKey.FFP_LIGHT_COUNT];
    private static int litCount;
    private static boolean normalize;
    private static float rescale;

    static {
        for (int i = 0; i < VertexKey.FFP_LIGHT_COUNT; i++) {
            lightDirs[i] = new Vector3f();
            lightDiffuse[i] = new Vector3f();
        }
    }

    private FFPVertexLighting() {}

    public static boolean modulatesVertexColor(Vector3f destFactor) {
        destFactor.set(1.0f);
        if (!prepare(false)) return false;

        factor(ShaderManager.getCurrentNormal(), scratchFactor);
        final float r = scratchFactor.x;
        final float g = scratchFactor.y;
        final float b = scratchFactor.z;
        if (r == 1.0f && g == 1.0f && b == 1.0f) return false;
        if (!Float.isFinite(r + g + b)) return false;
        destFactor.set(r, g, b);
        return true;
    }

    public static boolean bakeColors(int[] rawBuffer, int vertexCount, boolean hasNormals, boolean hasColor) {
        if (vertexCount <= 0 || !prepare(true)) return false;

        if (!hasNormals) factor(ShaderManager.getCurrentNormal(), scratchFactor);
        float cr = 0.0f, cg = 0.0f, cb = 0.0f;
        int alphaBits = 0;
        if (!hasColor) {
            final var color = GLSMCurrentAttribs.getColor();
            cr = clamp01(color.getRed());
            cg = clamp01(color.getGreen());
            cb = clamp01(color.getBlue());
            alphaBits = toByte(clamp01(color.getAlpha())) << 24;
        }

        boolean haveNormal = false;
        int lastNormal = 0;
        for (int v = 0, i = 0; v < vertexCount; v++, i += 8) {
            if (hasNormals) {
                final int packed = rawBuffer[i + 6];
                if (!haveNormal || packed != lastNormal) {
                    factor((byte) packed / 127.0f, (byte) (packed >> 8) / 127.0f, (byte) (packed >> 16) / 127.0f, scratchFactor);
                    lastNormal = packed;
                    haveNormal = true;
                }
            }
            if (hasColor) {
                final int packed = rawBuffer[i + 5];
                cr = (packed & 0xFF) / 255.0f;
                cg = ((packed >>> 8) & 0xFF) / 255.0f;
                cb = ((packed >>> 16) & 0xFF) / 255.0f;
                alphaBits = packed & 0xFF000000;
            }
            rawBuffer[i + 5] = alphaBits | (toByte(clamp01(cb * scratchFactor.z)) << 16) | (toByte(clamp01(cg * scratchFactor.y)) << 8) | toByte(clamp01(cr * scratchFactor.x));
        }
        return true;
    }

    private static boolean prepare(boolean directionalOnly) {
        if (!GLSMLighting.getLightingState().isEnabled()) return false;
        if (!GLSMLighting.getColorMaterial().isEnabled()) return false;
        if (VertexKey.encodeColorMaterialMode(GLSMLighting.getColorMaterialParameter().getValue()) != VertexKey.CM_AMBIENT_AND_DIFFUSE) return false;

        final MaterialState material = GLSMLighting.getFrontMaterial();
        if (material.emission.x != 0.0f || material.emission.y != 0.0f || material.emission.z != 0.0f)
            return false;

        final boolean materialHasSpecular = material.shininess > 0.0f && (material.specular.x != 0.0f || material.specular.y != 0.0f || material.specular.z != 0.0f);

        final Vector4f lightModelAmbient = GLSMLighting.getLightModel().ambient;
        base.set(lightModelAmbient.x, lightModelAmbient.y, lightModelAmbient.z);

        final var lightEnables = GLSMLighting.getLightStates();
        final var lightData = GLSMLighting.getLightDataStates();
        litCount = 0;

        for (int i = 0; i < VertexKey.FFP_LIGHT_COUNT; i++) {
            if (!lightEnables[i].isEnabled()) continue;
            final LightState light = lightData[i];
            if (materialHasSpecular && (light.specular.x != 0.0f || light.specular.y != 0.0f || light.specular.z != 0.0f))
                return false;
            if (directionalOnly && light.position.w != 0.0f) return false;
            base.add(light.ambient.x, light.ambient.y, light.ambient.z);
            lightDirs[litCount].set(lightDirection(light));
            lightDiffuse[litCount].set(light.diffuse.x, light.diffuse.y, light.diffuse.z);
            litCount++;
        }

        if (litCount > 0) {
            GLSMMatrices.getModelViewMatrix().normal(scratchNormalMat);
            normalize = GLSMLighting.getNormalizeState().isEnabled();
            rescale = !normalize && GLSMLighting.getRescaleNormalState().isEnabled() ? Uniforms.rescaleFactor(scratchNormalMat, scratchVec) : 1.0f;
        }
        return true;
    }

    private static void factor(Vector3f normal, Vector3f dest) {
        factor(normal.x, normal.y, normal.z, dest);
    }

    private static void factor(float nx, float ny, float nz, Vector3f dest) {
        dest.set(base);
        if (litCount == 0) return;
        scratchNormal.set(nx, ny, nz);
        scratchNormalMat.transform(scratchNormal);
        if (normalize) {
            scratchNormal.normalize();
        } else {
            scratchNormal.mul(rescale);
        }
        for (int i = 0; i < litCount; i++) {
            final float nDotVP = Math.max(scratchNormal.dot(lightDirs[i]), 0.0f);
            dest.fma(nDotVP, lightDiffuse[i]);
        }
    }

    private static float clamp01(float value) {
        return Math.min(Math.max(value, 0.0f), 1.0f);
    }

    private static int toByte(float unit) {
        return (int) (unit * 255.0f + 0.5f);
    }

    private static Vector3f lightDirection(LightState light) {
        if (light.position.w == 0.0f) {
            scratchLightVec.set(light.position.x, light.position.y, light.position.z);
        } else {
            scratchEyePos.set(0.0f, 0.0f, 0.0f, 1.0f).mul(GLSMMatrices.getModelViewMatrix());
            scratchLightVec.set(light.position.x - scratchEyePos.x, light.position.y - scratchEyePos.y, light.position.z - scratchEyePos.z);
        }
        return scratchLightVec.normalize();
    }
}
