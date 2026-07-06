package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.ExtLightDataAccess;
import com.gtnewhorizons.angelica.api.ExtQuadLightData;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.light.flat.FlatLightPipeline;
import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FlatLightPipeline.class, remap = false)
public class MixinFlatLightPipeline {

    @Shadow @Final private LightDataAccess lightCache;

    @Inject(method = "calculate", at = @At("TAIL"))
    private void angelica$populateRGBFlat(ModelQuadView quad, int x, int y, int z, QuadLightData out, ModelQuadFacing cullFace, ModelQuadFacing lightFace, boolean shade, boolean applyAoDepthBlending, CallbackInfo ci) {
        final ExtLightDataAccess rgbCache = (ExtLightDataAccess) this.lightCache;
        final ExtQuadLightData outExt = (ExtQuadLightData) out;

        final boolean blockEnabled = rgbCache.angelica$isRGBEnabled();
        final boolean skyEnabled = rgbCache.angelica$isSkyRGBEnabled();

        if (!blockEnabled && !skyEnabled) {
            outExt.angelica$setRGBValid(false);
            outExt.angelica$setSkyRGBValid(false);
            return;
        }

        final int lx, ly, lz;
        if (cullFace.isDirection()) {
            lx = x + cullFace.getStepX();
            ly = y + cullFace.getStepY();
            lz = z + cullFace.getStepZ();
        } else {
            final int flags = quad.getFlags();
            if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(x, y, z)))) {
                lx = x + lightFace.getStepX();
                ly = y + lightFace.getStepY();
                lz = z + lightFace.getStepZ();
            } else {
                lx = x;
                ly = y;
                lz = z;
            }
        }

        // Block RGB
        if (blockEnabled) {
            final int rgb = rgbCache.angelica$getRGB(lx, ly, lz);
            final float r = BlockLightProvider.unpackR(rgb), g = BlockLightProvider.unpackG(rgb), b = BlockLightProvider.unpackB(rgb);
            final float[] ra = outExt.angelica$getR(), ga = outExt.angelica$getG(), ba = outExt.angelica$getB();
            ra[0] = ra[1] = ra[2] = ra[3] = r;
            ga[0] = ga[1] = ga[2] = ga[3] = g;
            ba[0] = ba[1] = ba[2] = ba[3] = b;
            outExt.angelica$setRGBValid(true);
        } else {
            outExt.angelica$setRGBValid(false);
        }

        // Sky RGB
        if (skyEnabled) {
            final int skyRgb = rgbCache.angelica$getSkyRGB(lx, ly, lz);
            final float r = BlockLightProvider.unpackR(skyRgb), g = BlockLightProvider.unpackG(skyRgb), b = BlockLightProvider.unpackB(skyRgb);
            final float[] ra = outExt.angelica$getSkyR(), ga = outExt.angelica$getSkyG(), ba = outExt.angelica$getSkyB();
            ra[0] = ra[1] = ra[2] = ra[3] = r;
            ga[0] = ga[1] = ga[2] = ga[3] = g;
            ba[0] = ba[1] = ba[2] = ba[3] = b;
            outExt.angelica$setSkyRGBValid(true);
        } else {
            outExt.angelica$setSkyRGBValid(false);
        }
    }
}
