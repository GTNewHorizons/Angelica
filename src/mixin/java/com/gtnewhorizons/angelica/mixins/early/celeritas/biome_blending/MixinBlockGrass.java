package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import net.minecraft.block.BlockGrass;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockGrass.class)
public class MixinBlockGrass {
    @Surround(method = "colorMultiplier")
    private void celeritas$smoothBlendColor(IBlockAccess access, int x, int y, int z) {
        @Surround.Carry
        final long color = BiomeVertexBlender.smoothColor(access, SmoothBiomeColorCache.ColorType.GRASS, x, y, z);
        @Surround.Skip
        final boolean skip = color >= 0;
    }

    @Surround.Skipped
    private int celeritas$smoothBlendColorSkipped(@Surround.Carry long color) {
        return (int) color;
    }
}
