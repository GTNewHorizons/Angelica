package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.BlockLightProvider;
import com.gtnewhorizons.angelica.api.ExtLightDataAccess;
import com.gtnewhorizons.angelica.api.ExtLightDataCache;
import com.gtnewhorizons.angelica.api.SectionLightData;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(value = LightDataAccess.class, remap = false)
public abstract class MixinLightDataAccess implements ExtLightDataAccess {

    @Unique private static final int ANGELICA$CACHE_SIZE = 20 * 20 * 20;
    @Unique private static final int ANGELICA$SENTINEL = 0x1000;

    @Shadow
    private int index(int x, int y, int z) {
        throw new AssertionError();
    }

    /** Fused cache: upper 32 bits = block RGB word, lower 32 bits = sky RGB word. Zero = uncached. */
    @Unique private long[] angelica$fusedLight;

    @Unique private boolean angelica$rgbEnabled;

    @Unique private boolean angelica$skyRgbEnabled;

    @Unique private BlockLightProvider angelica$provider;

    @Unique private IBlockAccess angelica$blockAccess;

    @Inject(method = "reset", at = @At("TAIL"))
    private void angelica$resetRGB(int minBlockX, int minBlockY, int minBlockZ, CallbackInfo ci) {
        angelica$rgbEnabled = BlockLightProvider.isRegistered();
        angelica$provider = BlockLightProvider.getInstance();
        angelica$blockAccess = ((ExtLightDataCache) this).angelica$getBlockAccess();
        angelica$skyRgbEnabled = angelica$rgbEnabled;
        if (angelica$rgbEnabled) {
            if (angelica$fusedLight == null) {
                angelica$fusedLight = new long[ANGELICA$CACHE_SIZE];
            } else {
                Arrays.fill(angelica$fusedLight, 0L);
            }
        }
    }

    @Override
    public int angelica$getRGB(int x, int y, int z) {
        if (!angelica$rgbEnabled) return 0;
        final int idx = this.index(x, y, z);
        return angelica$getBlockFromFused(idx, x, y, z);
    }

    @Override
    public int angelica$getRGB(int x, int y, int z, ModelQuadFacing dir) {
        return angelica$getRGB(
            x + dir.getStepX(),
            y + dir.getStepY(),
            z + dir.getStepZ());
    }

    @Override
    public int angelica$getRGB(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2) {
        return angelica$getRGB(
            x + d1.getStepX() + d2.getStepX(),
            y + d1.getStepY() + d2.getStepY(),
            z + d1.getStepZ() + d2.getStepZ());
    }

    @Override
    public boolean angelica$isRGBEnabled() {
        return angelica$rgbEnabled;
    }

    @Override
    public int angelica$getSkyRGB(int x, int y, int z) {
        if (!angelica$skyRgbEnabled) return 0;
        final int idx = this.index(x, y, z);
        return angelica$getSkyFromFused(idx, x, y, z);
    }

    @Override
    public int angelica$getSkyRGB(int x, int y, int z, ModelQuadFacing dir) {
        return angelica$getSkyRGB(
            x + dir.getStepX(),
            y + dir.getStepY(),
            z + dir.getStepZ());
    }

    @Override
    public int angelica$getSkyRGB(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2) {
        return angelica$getSkyRGB(
            x + d1.getStepX() + d2.getStepX(),
            y + d1.getStepY() + d2.getStepY(),
            z + d1.getStepZ() + d2.getStepZ());
    }

    @Override
    public boolean angelica$isSkyRGBEnabled() {
        return angelica$skyRgbEnabled;
    }

    @Override
    public int angelica$getIndex(int x, int y, int z) {
        return this.index(x, y, z);
    }

    @Override
    public int angelica$getRGBByIndex(int idx, int x, int y, int z) {
        if (!angelica$rgbEnabled) return 0;
        return angelica$getBlockFromFused(idx, x, y, z);
    }

    @Override
    public int angelica$getSkyRGBByIndex(int idx, int x, int y, int z) {
        if (!angelica$skyRgbEnabled) return 0;
        return angelica$getSkyFromFused(idx, x, y, z);
    }

    @Override
    public long angelica$getFusedByIndex(int idx, int x, int y, int z) {
        long fused = angelica$fusedLight[idx];
        if (fused == 0L) {
            fused = angelica$fusedLight[idx] = angelica$computeFused(x, y, z);
        }
        return fused;
    }

    @Unique
    private int angelica$getBlockFromFused(int idx, int x, int y, int z) {
        long fused = angelica$fusedLight[idx];
        if (fused == 0L) {
            fused = angelica$fusedLight[idx] = angelica$computeFused(x, y, z);
        }
        return (int)(fused >>> 32);
    }

    @Unique
    private int angelica$getSkyFromFused(int idx, int x, int y, int z) {
        long fused = angelica$fusedLight[idx];
        if (fused == 0L) {
            fused = angelica$fusedLight[idx] = angelica$computeFused(x, y, z);
        }
        return (int)(fused & 0xFFFFFFFFL);
    }

    @Unique
    private long angelica$computeFused(int x, int y, int z) {
        final IBlockAccess blockAccess = angelica$blockAccess;

        if (blockAccess instanceof WorldSlice ws) {
            int blockRGB = 0xFFFF; // sentinel: no data
            int skyRGB = 0xFFFF;

            final SectionLightData data = ws.getSectionLightData(x, y, z);
            if (data != null) {
                final long fused = data.getRGBAndSkyRGB(x & 15, y & 15, z & 15);
                blockRGB = (int)(fused >>> 16) & 0xFFFF;
                skyRGB = (int)fused & 0xFFFF;
            }

            // Overlay dynamic light RGB (thread-safe: uses pre-filtered chunk-local sources)
            final var dlSources = ws.getChunkLightSources();
            if (dlSources != null && !dlSources.isEmpty()) {
                final int dlRGB = ws.getDynamicLightsInstance()
                    .getDynamicLightRGBFromSources(x + 0.5, y + 0.5, z + 0.5, dlSources);
                if (dlRGB != 0) {
                    blockRGB = angelica$mergeMaxRGB(blockRGB, dlRGB);
                }
            }

            final long blockWord = (blockRGB == 0xFFFF) ? ANGELICA$SENTINEL : (blockRGB | ANGELICA$SENTINEL);
            final long skyWord = (skyRGB == 0xFFFF) ? ANGELICA$SENTINEL : (skyRGB | ANGELICA$SENTINEL);
            return (blockWord << 32) | (skyWord & 0xFFFFFFFFL);
        }

        final BlockLightProvider provider = angelica$provider;
        int brgb = provider.getBlockLightRGB(blockAccess, x, y, z);
        final int srgb = provider.getSkyLightRGB(blockAccess, x, y, z);

        if (DynamicLights.isEnabled()) {
            final int dlRGB = DynamicLights.get().getDynamicLightRGB(x + 0.5, y + 0.5, z + 0.5);
            if (dlRGB != 0) {
                brgb = angelica$mergeMaxRGB(brgb, dlRGB);
            }
        }

        final long blockWord = (brgb == -1) ? ANGELICA$SENTINEL : (brgb | ANGELICA$SENTINEL);
        final long skyWord = (srgb == -1) ? ANGELICA$SENTINEL : (srgb | ANGELICA$SENTINEL);
        return (blockWord << 32) | (skyWord & 0xFFFFFFFFL);
    }

    /** Max-per-channel merge. Treats sentinel (0xFFFF or -1) as (0,0,0). */
    @Unique
    private static int angelica$mergeMaxRGB(int staticRGB, int dynamicRGB) {
        final int sR = (staticRGB == 0xFFFF || staticRGB == -1) ? 0 : BlockLightProvider.unpackR(staticRGB);
        final int sG = (staticRGB == 0xFFFF || staticRGB == -1) ? 0 : BlockLightProvider.unpackG(staticRGB);
        final int sB = (staticRGB == 0xFFFF || staticRGB == -1) ? 0 : BlockLightProvider.unpackB(staticRGB);
        return BlockLightProvider.packRGB(
            Math.max(sR, BlockLightProvider.unpackR(dynamicRGB)),
            Math.max(sG, BlockLightProvider.unpackG(dynamicRGB)),
            Math.max(sB, BlockLightProvider.unpackB(dynamicRGB)));
    }
}
