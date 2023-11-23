package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Material.class)
public interface MixinMaterial {

    @Accessor("isTranslucent")
    boolean getIsTranslucent();
}
