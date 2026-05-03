package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.ExtAoFaceData;
import com.gtnewhorizons.angelica.api.ExtLightDataAccess;
import com.gtnewhorizons.angelica.api.ExtQuadLightData;
import com.gtnewhorizons.angelica.rendering.celeritas.compat.AoHelper;
import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.common.util.MathUtil;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.light.smooth.AoFaceData;
import org.embeddedt.embeddium.impl.model.light.smooth.SmoothLightPipeline;
import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SmoothLightPipeline.class, remap = false)
public abstract class MixinSmoothLightPipeline {

    @Shadow @Final private LightDataAccess lightCache;
    @Shadow @Final private float[] weights;
    @Shadow @Final private AoFaceData[] cachedFaceData;

    @Unique private float angelica$lastR;
    @Unique private float angelica$lastG;
    @Unique private float angelica$lastB;

    @Unique private float angelica$lastSkyR;
    @Unique private float angelica$lastSkyG;
    @Unique private float angelica$lastSkyB;

    @Unique
    private ExtAoFaceData angelica$getFaceData(ModelQuadFacing face, boolean offset) {
        final int idx = offset ? face.ordinal() : face.ordinal() + 6;
        return (ExtAoFaceData) cachedFaceData[idx];
    }

    @Inject(method = "calculate", at = @At("TAIL"))
    private void angelica$populateRGB(ModelQuadView quad, int x, int y, int z, QuadLightData out, ModelQuadFacing cullFace, ModelQuadFacing lightFace, boolean shade, boolean applyAoDepthBlending, CallbackInfo ci) {
        final ExtLightDataAccess rgbCache = (ExtLightDataAccess) lightCache;
        final ExtQuadLightData outExt = (ExtQuadLightData) out;
        final boolean doBlock = rgbCache.angelica$isRGBEnabled();
        final boolean doSky = rgbCache.angelica$isSkyRGBEnabled();

        if (!doBlock && !doSky) {
            outExt.angelica$setRGBValid(false);
            outExt.angelica$setSkyRGBValid(false);
            return;
        }

        final int flags = quad.getFlags();
        final boolean isAligned = (flags & ModelQuadFlags.IS_ALIGNED) != 0;
        final boolean isParallel = (flags & ModelQuadFlags.IS_PARALLEL) != 0;
        final boolean isPartial = (flags & ModelQuadFlags.IS_PARTIAL) != 0;
        final boolean fullCube = isParallel && LightDataAccess.unpackFC(this.lightCache.get(x, y, z));

        if (isAligned || fullCube) {
            if (!isPartial) {
                angelica$alignedFullFace(lightFace, outExt, doBlock, doSky);
            } else {
                angelica$perVertexFace(quad, lightFace, outExt, false, true, doBlock, doSky);
            }
        } else {
            if ((flags & ModelQuadFlags.IS_VANILLA_SHADED) == 0
                && quad.getNormalFace() == ModelQuadFacing.UNASSIGNED) {
                angelica$irregularFace(quad, outExt, applyAoDepthBlending, doBlock, doSky);
            } else {
                angelica$perVertexFace(quad, lightFace, outExt, applyAoDepthBlending, false, doBlock, doSky);
            }
        }

        outExt.angelica$setRGBValid(doBlock);
        outExt.angelica$setSkyRGBValid(doSky);
    }

    @Unique
    private void angelica$alignedFullFace(ModelQuadFacing dir, ExtQuadLightData out, boolean doBlock, boolean doSky) {
        final ExtAoFaceData ext = angelica$getFaceData(dir, true);
        final int[] remap = AoHelper.CORNER_REMAP[dir.ordinal()];

        if (doBlock) {
            final float[] rl = ext.angelica$getRl(), gl = ext.angelica$getGl(), bbl = ext.angelica$getBl();
            final float[] r = out.angelica$getR(), g = out.angelica$getG(), b = out.angelica$getB();
            for (int i = 0; i < 4; i++) {
                r[i] = rl[remap[i]];
                g[i] = gl[remap[i]];
                b[i] = bbl[remap[i]];
            }
        }
        if (doSky) {
            final float[] rl = ext.angelica$getSkyRl(), gl = ext.angelica$getSkyGl(), bbl = ext.angelica$getSkyBl();
            final float[] r = out.angelica$getSkyR(), g = out.angelica$getSkyG(), b = out.angelica$getSkyB();
            for (int i = 0; i < 4; i++) {
                r[i] = rl[remap[i]];
                g[i] = gl[remap[i]];
                b[i] = bbl[remap[i]];
            }
        }
    }

    @Unique
    private void angelica$perVertexFace(ModelQuadView quad, ModelQuadFacing dir, ExtQuadLightData out,
            boolean useDepthBlending, boolean alignedPartial, boolean doBlock, boolean doSky) {
        final float[] br = doBlock ? out.angelica$getR() : null;
        final float[] bg = doBlock ? out.angelica$getG() : null;
        final float[] bb = doBlock ? out.angelica$getB() : null;
        final float[] sr = doSky ? out.angelica$getSkyR() : null;
        final float[] sg = doSky ? out.angelica$getSkyG() : null;
        final float[] sb = doSky ? out.angelica$getSkyB() : null;

        for (int i = 0; i < 4; i++) {
            final float cx = angelica$clamp(quad.getX(i));
            final float cy = angelica$clamp(quad.getY(i));
            final float cz = angelica$clamp(quad.getZ(i));

            AoHelper.calculateCornerWeights(dir, cx, cy, cz, this.weights);
            final float depth = AoHelper.getDepth(dir, cx, cy, cz);

            if (useDepthBlending) {
                angelica$blendInset(dir, depth, 1.0f - depth, this.weights, doBlock, doSky);
            } else {
                angelica$blendAligned(dir, this.weights,
                    alignedPartial || MathUtil.roughlyEqual(depth, 0.0f), doBlock, doSky);
            }

            if (doBlock) {
                br[i] = this.angelica$lastR;
                bg[i] = this.angelica$lastG;
                bb[i] = this.angelica$lastB;
            }
            if (doSky) {
                sr[i] = this.angelica$lastSkyR;
                sg[i] = this.angelica$lastSkyG;
                sb[i] = this.angelica$lastSkyB;
            }
        }
    }

    @Unique
    private void angelica$irregularFace(ModelQuadView quad, ExtQuadLightData out,
            boolean applyAoDepthBlending, boolean doBlock, boolean doSky) {
        final float[] br = doBlock ? out.angelica$getR() : null;
        final float[] bg = doBlock ? out.angelica$getG() : null;
        final float[] bb = doBlock ? out.angelica$getB() : null;
        final float[] sr = doSky ? out.angelica$getSkyR() : null;
        final float[] sg = doSky ? out.angelica$getSkyG() : null;
        final float[] sb = doSky ? out.angelica$getSkyB() : null;

        for (int i = 0; i < 4; i++) {
            final float cx = angelica$clamp(quad.getX(i));
            final float cy = angelica$clamp(quad.getY(i));
            final float cz = angelica$clamp(quad.getZ(i));

            int normal = quad.getForgeNormal(i);
            if (normal == 0) {
                normal = quad.getComputedFaceNormal();
            }

            float bWeightedR = 0, bWeightedG = 0, bWeightedB = 0;
            float bMaxR = 0, bMaxG = 0, bMaxB = 0;
            float sWeightedR = 0, sWeightedG = 0, sWeightedB = 0;
            float sMaxR = 0, sMaxG = 0, sMaxB = 0;

            for (int axis = 0; axis < 3; axis++) {
                final float projectedNormal = NormI8.unpackX(normal >> (axis * 8));
                if (projectedNormal == 0) {
                    continue;
                }

                final ModelQuadFacing dir = ModelQuadFacing.AXES[axis].getFacing(projectedNormal > 0);

                AoHelper.calculateCornerWeights(dir, cx, cy, cz, this.weights);
                final float depth = AoHelper.getDepth(dir, cx, cy, cz);

                if (applyAoDepthBlending) {
                    angelica$blendInset(dir, depth, 1.0f - depth, this.weights, doBlock, doSky);
                } else {
                    angelica$blendAligned(dir, this.weights,
                        MathUtil.roughlyEqual(depth, 0.0f), doBlock, doSky);
                }

                final float combineWeight = projectedNormal * projectedNormal;

                if (doBlock) {
                    bWeightedR += this.angelica$lastR * combineWeight;
                    bWeightedG += this.angelica$lastG * combineWeight;
                    bWeightedB += this.angelica$lastB * combineWeight;
                    bMaxR = Math.max(this.angelica$lastR, bMaxR);
                    bMaxG = Math.max(this.angelica$lastG, bMaxG);
                    bMaxB = Math.max(this.angelica$lastB, bMaxB);
                }
                if (doSky) {
                    sWeightedR += this.angelica$lastSkyR * combineWeight;
                    sWeightedG += this.angelica$lastSkyG * combineWeight;
                    sWeightedB += this.angelica$lastSkyB * combineWeight;
                    sMaxR = Math.max(this.angelica$lastSkyR, sMaxR);
                    sMaxG = Math.max(this.angelica$lastSkyG, sMaxG);
                    sMaxB = Math.max(this.angelica$lastSkyB, sMaxB);
                }
            }

            if (doBlock) {
                br[i] = bWeightedR * 0.75f + bMaxR * 0.25f;
                bg[i] = bWeightedG * 0.75f + bMaxG * 0.25f;
                bb[i] = bWeightedB * 0.75f + bMaxB * 0.25f;
            }
            if (doSky) {
                sr[i] = sWeightedR * 0.75f + sMaxR * 0.25f;
                sg[i] = sWeightedG * 0.75f + sMaxG * 0.25f;
                sb[i] = sWeightedB * 0.75f + sMaxB * 0.25f;
            }
        }
    }

    @Unique
    private void angelica$blendAligned(ModelQuadFacing dir, float[] w, boolean offset, boolean doBlock, boolean doSky) {
        final ExtAoFaceData ext = angelica$getFaceData(dir, offset);
        if (doBlock) {
            this.angelica$lastR = ext.angelica$getBlendedR(w);
            this.angelica$lastG = ext.angelica$getBlendedG(w);
            this.angelica$lastB = ext.angelica$getBlendedB(w);
        }
        if (doSky) {
            this.angelica$lastSkyR = ext.angelica$getBlendedSkyR(w);
            this.angelica$lastSkyG = ext.angelica$getBlendedSkyG(w);
            this.angelica$lastSkyB = ext.angelica$getBlendedSkyB(w);
        }
    }

    @Unique
    private void angelica$blendInset(ModelQuadFacing dir, float n1d, float n2d, float[] w, boolean doBlock, boolean doSky) {
        if (MathUtil.roughlyEqual(n1d, 0.0f)) {
            angelica$blendAligned(dir, w, true, doBlock, doSky);
            return;
        }
        if (MathUtil.roughlyEqual(n1d, 1.0f)) {
            angelica$blendAligned(dir, w, false, doBlock, doSky);
            return;
        }

        final ExtAoFaceData n1e = angelica$getFaceData(dir, false);
        final ExtAoFaceData n2e = angelica$getFaceData(dir, true);

        if (doBlock) {
            this.angelica$lastR = n1e.angelica$getBlendedR(w) * n1d + n2e.angelica$getBlendedR(w) * n2d;
            this.angelica$lastG = n1e.angelica$getBlendedG(w) * n1d + n2e.angelica$getBlendedG(w) * n2d;
            this.angelica$lastB = n1e.angelica$getBlendedB(w) * n1d + n2e.angelica$getBlendedB(w) * n2d;
        }
        if (doSky) {
            this.angelica$lastSkyR = n1e.angelica$getBlendedSkyR(w) * n1d + n2e.angelica$getBlendedSkyR(w) * n2d;
            this.angelica$lastSkyG = n1e.angelica$getBlendedSkyG(w) * n1d + n2e.angelica$getBlendedSkyG(w) * n2d;
            this.angelica$lastSkyB = n1e.angelica$getBlendedSkyB(w) * n1d + n2e.angelica$getBlendedSkyB(w) * n2d;
        }
    }

    @Unique
    private static float angelica$clamp(float v) {
        if (v < 0.0f) {
            return 0.0f;
        } else if (v > 1.0f) {
            return 1.0f;
        }
        return v;
    }
}
