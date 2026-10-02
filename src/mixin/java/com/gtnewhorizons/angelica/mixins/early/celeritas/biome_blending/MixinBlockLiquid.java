package com.gtnewhorizons.angelica.mixins.early.celeritas.biome_blending;

import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.WorldClientExtension;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    @WrapMethod(method = "colorMultiplier")
    private int celeritas$smoothBlendColor(IBlockAccess access, int x, int y, int z, Operation<Integer> original) {
        if (this.blockMaterial != Material.water) {
            return original.call(access, x, y, z);
        }
        // Check ThreadLocal first (for worker threads), then fall back to world cache
        SmoothBiomeColorCache cache = SmoothBiomeColorCache.getActiveCache();
        if (cache != null) {
            return cache.getColor(SmoothBiomeColorCache.ColorType.WATER, x, y, z);
        } else if (access instanceof WorldClientExtension ext) {
            return ext.celeritas$getSmoothBiomeColorCache().getColor(SmoothBiomeColorCache.ColorType.WATER, x, y, z);
        } else {
            return original.call(access, x, y, z);
        }
    }
}
