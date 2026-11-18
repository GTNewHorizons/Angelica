package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import net.minecraft.block.BlockLeaves;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockLeaves.class)
public class MixinBlockLeaves {
    @Surround(method = "colorMultiplier")
    private void celeritas$smoothBlendColor(IBlockAccess access, int x, int y, int z) {
        @Surround.Carry
        final long color = BiomeVertexBlender.smoothColor(access, SmoothBiomeColorCache.ColorType.FOLIAGE, x, y, z);
        @Surround.Skip
        final boolean skip = color >= 0;
    }

    @Surround.Skipped
    private int celeritas$smoothBlendColorSkipped(@Surround.Carry long color) {
        return (int) color;
    }
}
