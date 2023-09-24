package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MaterialLiquid.class)
public abstract class MixinMaterialLiquid extends Material {
    public MixinMaterialLiquid(MapColor p_i2116_1_) {
        super(p_i2116_1_);
    }

    @Override
    public boolean getCanBlockGrass() {
        return false;
    }
}
