package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.celeritas.BiomeVertexBlender;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockLiquid.class)
public abstract class MixinBlockLiquid extends Block {
    protected MixinBlockLiquid(Material material) {
        super(material);
    }

    @Surround(method = "colorMultiplier")
    private void celeritas$smoothBlendColor(IBlockAccess access, int x, int y, int z) {
        @Surround.Carry
        final long color = this.blockMaterial == Material.water ? BiomeVertexBlender.smoothColor(access, SmoothBiomeColorCache.ColorType.WATER, x, y, z) : -1L;
        @Surround.Skip
        final boolean skip = color >= 0;
    }

    @Surround.Skipped
    private int celeritas$smoothBlendColorSkipped(@Surround.Carry long color) {
        return (int) color;
    }
}
