package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.ExtAoFaceData;
import com.gtnewhorizons.angelica.api.ExtLightDataAccess;
import com.gtnewhorizons.angelica.rendering.celeritas.compat.AoHelper;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.smooth.AoFaceData;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AoFaceData.class, remap = false)
public class MixinAoFaceData implements ExtAoFaceData {

    @Unique private final float[] angelica$rl = new float[4];
    @Unique private final float[] angelica$gl = new float[4];
    @Unique private final float[] angelica$bl = new float[4];
    @Unique private final float[] angelica$skyRl = new float[4];
    @Unique private final float[] angelica$skyGl = new float[4];
    @Unique private final float[] angelica$skyBl = new float[4];

    @Override
    public float[] angelica$getRl() {
        return angelica$rl;
    }

    @Override
    public float[] angelica$getGl() {
        return angelica$gl;
    }

    @Override
    public float[] angelica$getBl() {
        return angelica$bl;
    }

    @Override
    public float angelica$getBlendedR(float[] w) {
        return angelica$rl[0] * w[0] + angelica$rl[1] * w[1] + angelica$rl[2] * w[2] + angelica$rl[3] * w[3];
    }

    @Override
    public float angelica$getBlendedG(float[] w) {
        return angelica$gl[0] * w[0] + angelica$gl[1] * w[1] + angelica$gl[2] * w[2] + angelica$gl[3] * w[3];
    }

    @Override
    public float angelica$getBlendedB(float[] w) {
        return angelica$bl[0] * w[0] + angelica$bl[1] * w[1] + angelica$bl[2] * w[2] + angelica$bl[3] * w[3];
    }

    @Override
    public float[] angelica$getSkyRl() {
        return angelica$skyRl;
    }

    @Override
    public float[] angelica$getSkyGl() {
        return angelica$skyGl;
    }

    @Override
    public float[] angelica$getSkyBl() {
        return angelica$skyBl;
    }

    @Override
    public float angelica$getBlendedSkyR(float[] w) {
        return angelica$skyRl[0] * w[0] + angelica$skyRl[1] * w[1] + angelica$skyRl[2] * w[2] + angelica$skyRl[3] * w[3];
    }

    @Override
    public float angelica$getBlendedSkyG(float[] w) {
        return angelica$skyGl[0] * w[0] + angelica$skyGl[1] * w[1] + angelica$skyGl[2] * w[2] + angelica$skyGl[3] * w[3];
    }

    @Override
    public float angelica$getBlendedSkyB(float[] w) {
        return angelica$skyBl[0] * w[0] + angelica$skyBl[1] * w[1] + angelica$skyBl[2] * w[2] + angelica$skyBl[3] * w[3];
    }

    @Inject(method = "initLightData", at = @At("TAIL"))
    private void angelica$initRGBData(LightDataAccess cache, int x, int y, int z, ModelQuadFacing direction, boolean offset, CallbackInfo ci) {
        final ExtLightDataAccess rgbCache = (ExtLightDataAccess) cache;
        final boolean doBlock = rgbCache.angelica$isRGBEnabled();
        final boolean doSky = rgbCache.angelica$isSkyRGBEnabled();
        if (!doBlock && !doSky) return;

        final int adjX, adjY, adjZ;

        if (offset) {
            adjX = x + direction.getStepX();
            adjY = y + direction.getStepY();
            adjZ = z + direction.getStepZ();
        } else {
            adjX = x;
            adjY = y;
            adjZ = z;
        }

        final ModelQuadFacing[] faces = AoHelper.getFaces(direction);

        // Edge opacity
        final boolean e0op = LightDataAccess.unpackOP(cache.get(adjX, adjY, adjZ, faces[0]));
        final boolean e1op = LightDataAccess.unpackOP(cache.get(adjX, adjY, adjZ, faces[1]));
        final boolean e2op = LightDataAccess.unpackOP(cache.get(adjX, adjY, adjZ, faces[2]));
        final boolean e3op = LightDataAccess.unpackOP(cache.get(adjX, adjY, adjZ, faces[3]));

        // Center
        final int adjWord = cache.get(adjX, adjY, adjZ);
        final boolean useFallback = offset && LightDataAccess.unpackFO(adjWord);
        final int caX = useFallback ? x : adjX;
        final int caY = useFallback ? y : adjY;
        final int caZ = useFallback ? z : adjZ;
        final int caIdx = rgbCache.angelica$getIndex(caX, caY, caZ);

        // Edge world coords
        final int e0x = adjX + faces[0].getStepX(), e0y = adjY + faces[0].getStepY(), e0z = adjZ + faces[0].getStepZ();
        final int e1x = adjX + faces[1].getStepX(), e1y = adjY + faces[1].getStepY(), e1z = adjZ + faces[1].getStepZ();
        final int e2x = adjX + faces[2].getStepX(), e2y = adjY + faces[2].getStepY(), e2z = adjZ + faces[2].getStepZ();
        final int e3x = adjX + faces[3].getStepX(), e3y = adjY + faces[3].getStepY(), e3z = adjZ + faces[3].getStepZ();

        // Edge indices
        final int e0Idx = rgbCache.angelica$getIndex(e0x, e0y, e0z);
        final int e1Idx = rgbCache.angelica$getIndex(e1x, e1y, e1z);
        final int e2Idx = rgbCache.angelica$getIndex(e2x, e2y, e2z);
        final int e3Idx = rgbCache.angelica$getIndex(e3x, e3y, e3z);

        // Corner world coords + indices; only compute if not occluded
        final int c0x, c0y, c0z, c0Idx;
        if (e2op && e0op) { c0x = c0y = c0z = c0Idx = 0; }
        else {
            c0x = e0x + faces[2].getStepX(); c0y = e0y + faces[2].getStepY(); c0z = e0z + faces[2].getStepZ();
            c0Idx = rgbCache.angelica$getIndex(c0x, c0y, c0z);
        }

        final int c1x, c1y, c1z, c1Idx;
        if (e3op && e0op) { c1x = c1y = c1z = c1Idx = 0; }
        else {
            c1x = e0x + faces[3].getStepX(); c1y = e0y + faces[3].getStepY(); c1z = e0z + faces[3].getStepZ();
            c1Idx = rgbCache.angelica$getIndex(c1x, c1y, c1z);
        }

        final int c2x, c2y, c2z, c2Idx;
        if (e2op && e1op) { c2x = c2y = c2z = c2Idx = 0; }
        else {
            c2x = e1x + faces[2].getStepX(); c2y = e1y + faces[2].getStepY(); c2z = e1z + faces[2].getStepZ();
            c2Idx = rgbCache.angelica$getIndex(c2x, c2y, c2z);
        }

        final int c3x, c3y, c3z, c3Idx;
        if (e3op && e1op) { c3x = c3y = c3z = c3Idx = 0; }
        else {
            c3x = e1x + faces[3].getStepX(); c3y = e1y + faces[3].getStepY(); c3z = e1z + faces[3].getStepZ();
            c3Idx = rgbCache.angelica$getIndex(c3x, c3y, c3z);
        }

        final long caF = rgbCache.angelica$getFusedByIndex(caIdx, caX, caY, caZ);
        final long e0F = rgbCache.angelica$getFusedByIndex(e0Idx, e0x, e0y, e0z);
        final long e1F = rgbCache.angelica$getFusedByIndex(e1Idx, e1x, e1y, e1z);
        final long e2F = rgbCache.angelica$getFusedByIndex(e2Idx, e2x, e2y, e2z);
        final long e3F = rgbCache.angelica$getFusedByIndex(e3Idx, e3x, e3y, e3z);
        final long c0F = (e2op && e0op) ? e0F : rgbCache.angelica$getFusedByIndex(c0Idx, c0x, c0y, c0z);
        final long c1F = (e3op && e0op) ? e0F : rgbCache.angelica$getFusedByIndex(c1Idx, c1x, c1y, c1z);
        final long c2F = (e2op && e1op) ? e1F : rgbCache.angelica$getFusedByIndex(c2Idx, c2x, c2y, c2z);
        final long c3F = (e3op && e1op) ? e1F : rgbCache.angelica$getFusedByIndex(c3Idx, c3x, c3y, c3z);

        if (doBlock) {
            final int caRGB = (int)(caF >>> 32);
            final int e0RGB = (int)(e0F >>> 32);
            final int e1RGB = (int)(e1F >>> 32);
            final int e2RGB = (int)(e2F >>> 32);
            final int e3RGB = (int)(e3F >>> 32);
            final int c0RGB = (int)(c0F >>> 32);
            final int c1RGB = (int)(c1F >>> 32);
            final int c2RGB = (int)(c2F >>> 32);
            final int c3RGB = (int)(c3F >>> 32);

            final int caR = BlockLightProvider.unpackR(caRGB), caG = BlockLightProvider.unpackG(caRGB), caB = BlockLightProvider.unpackB(caRGB);
            final int e0R = BlockLightProvider.unpackR(e0RGB), e0G = BlockLightProvider.unpackG(e0RGB), e0B = BlockLightProvider.unpackB(e0RGB);
            final int e1R = BlockLightProvider.unpackR(e1RGB), e1G = BlockLightProvider.unpackG(e1RGB), e1B = BlockLightProvider.unpackB(e1RGB);
            final int e2R = BlockLightProvider.unpackR(e2RGB), e2G = BlockLightProvider.unpackG(e2RGB), e2B = BlockLightProvider.unpackB(e2RGB);
            final int e3R = BlockLightProvider.unpackR(e3RGB), e3G = BlockLightProvider.unpackG(e3RGB), e3B = BlockLightProvider.unpackB(e3RGB);
            final int c0R = BlockLightProvider.unpackR(c0RGB), c0G = BlockLightProvider.unpackG(c0RGB), c0B = BlockLightProvider.unpackB(c0RGB);
            final int c1R = BlockLightProvider.unpackR(c1RGB), c1G = BlockLightProvider.unpackG(c1RGB), c1B = BlockLightProvider.unpackB(c1RGB);
            final int c2R = BlockLightProvider.unpackR(c2RGB), c2G = BlockLightProvider.unpackG(c2RGB), c2B = BlockLightProvider.unpackB(c2RGB);
            final int c3R = BlockLightProvider.unpackR(c3RGB), c3G = BlockLightProvider.unpackG(c3RGB), c3B = BlockLightProvider.unpackB(c3RGB);

            angelica$rl[0]  = (e3R + e0R + c1R + caR) * 0.25f;
            angelica$gl[0]  = (e3G + e0G + c1G + caG) * 0.25f;
            angelica$bl[0] = (e3B + e0B + c1B + caB) * 0.25f;

            angelica$rl[1]  = (e2R + e0R + c0R + caR) * 0.25f;
            angelica$gl[1]  = (e2G + e0G + c0G + caG) * 0.25f;
            angelica$bl[1] = (e2B + e0B + c0B + caB) * 0.25f;

            angelica$rl[2]  = (e2R + e1R + c2R + caR) * 0.25f;
            angelica$gl[2]  = (e2G + e1G + c2G + caG) * 0.25f;
            angelica$bl[2] = (e2B + e1B + c2B + caB) * 0.25f;

            angelica$rl[3]  = (e3R + e1R + c3R + caR) * 0.25f;
            angelica$gl[3]  = (e3G + e1G + c3G + caG) * 0.25f;
            angelica$bl[3] = (e3B + e1B + c3B + caB) * 0.25f;
        }

        if (doSky) {
            final int caSky = (int)(caF & 0xFFFFFFFFL);
            final int e0Sky = (int)(e0F & 0xFFFFFFFFL);
            final int e1Sky = (int)(e1F & 0xFFFFFFFFL);
            final int e2Sky = (int)(e2F & 0xFFFFFFFFL);
            final int e3Sky = (int)(e3F & 0xFFFFFFFFL);
            final int c0Sky = (int)(c0F & 0xFFFFFFFFL);
            final int c1Sky = (int)(c1F & 0xFFFFFFFFL);
            final int c2Sky = (int)(c2F & 0xFFFFFFFFL);
            final int c3Sky = (int)(c3F & 0xFFFFFFFFL);

            final int caR = BlockLightProvider.unpackR(caSky), caG = BlockLightProvider.unpackG(caSky), caB = BlockLightProvider.unpackB(caSky);
            final int e0R = BlockLightProvider.unpackR(e0Sky), e0G = BlockLightProvider.unpackG(e0Sky), e0B = BlockLightProvider.unpackB(e0Sky);
            final int e1R = BlockLightProvider.unpackR(e1Sky), e1G = BlockLightProvider.unpackG(e1Sky), e1B = BlockLightProvider.unpackB(e1Sky);
            final int e2R = BlockLightProvider.unpackR(e2Sky), e2G = BlockLightProvider.unpackG(e2Sky), e2B = BlockLightProvider.unpackB(e2Sky);
            final int e3R = BlockLightProvider.unpackR(e3Sky), e3G = BlockLightProvider.unpackG(e3Sky), e3B = BlockLightProvider.unpackB(e3Sky);
            final int c0R = BlockLightProvider.unpackR(c0Sky), c0G = BlockLightProvider.unpackG(c0Sky), c0B = BlockLightProvider.unpackB(c0Sky);
            final int c1R = BlockLightProvider.unpackR(c1Sky), c1G = BlockLightProvider.unpackG(c1Sky), c1B = BlockLightProvider.unpackB(c1Sky);
            final int c2R = BlockLightProvider.unpackR(c2Sky), c2G = BlockLightProvider.unpackG(c2Sky), c2B = BlockLightProvider.unpackB(c2Sky);
            final int c3R = BlockLightProvider.unpackR(c3Sky), c3G = BlockLightProvider.unpackG(c3Sky), c3B = BlockLightProvider.unpackB(c3Sky);

            angelica$skyRl[0]  = (e3R + e0R + c1R + caR) * 0.25f;
            angelica$skyGl[0]  = (e3G + e0G + c1G + caG) * 0.25f;
            angelica$skyBl[0] = (e3B + e0B + c1B + caB) * 0.25f;

            angelica$skyRl[1]  = (e2R + e0R + c0R + caR) * 0.25f;
            angelica$skyGl[1]  = (e2G + e0G + c0G + caG) * 0.25f;
            angelica$skyBl[1] = (e2B + e0B + c0B + caB) * 0.25f;

            angelica$skyRl[2]  = (e2R + e1R + c2R + caR) * 0.25f;
            angelica$skyGl[2]  = (e2G + e1G + c2G + caG) * 0.25f;
            angelica$skyBl[2] = (e2B + e1B + c2B + caB) * 0.25f;

            angelica$skyRl[3]  = (e3R + e1R + c3R + caR) * 0.25f;
            angelica$skyGl[3]  = (e3G + e1G + c3G + caG) * 0.25f;
            angelica$skyBl[3] = (e3B + e1B + c3B + caB) * 0.25f;
        }
    }

}
