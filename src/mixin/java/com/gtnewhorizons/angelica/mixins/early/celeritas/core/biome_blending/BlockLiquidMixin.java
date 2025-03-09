package com.gtnewhorizons.angelica.mixins.early.celeritas.core.biome_blending;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.taumc.celeritas.impl.extensions.WorldClientExtension;
import org.taumc.celeritas.impl.world.biome.SmoothBiomeColorCache;

@Mixin(BlockLiquid.class)
public abstract class BlockLiquidMixin extends Block {
    protected BlockLiquidMixin(Material p_i45394_1_) {
        super(p_i45394_1_);
    }

    @WrapMethod(method = "colorMultiplier")
    private int smoothBlendColor(IBlockAccess access, int x, int y, int z, Operation<Integer> original) {
        if (SmoothBiomeColorCache.enabled && this.blockMaterial == Material.water && access instanceof WorldClientExtension ext) {
            return ext.celeritas$getSmoothBiomeColorCache().getColor(SmoothBiomeColorCache.ColorType.WATER, x, y, z);
        } else {
            return original.call(access, x, y, z);
        }
    }
}
