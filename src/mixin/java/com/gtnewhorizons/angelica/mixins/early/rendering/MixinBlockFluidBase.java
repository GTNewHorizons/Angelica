package com.gtnewhorizons.angelica.mixins.early.rendering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraftforge.fluids.BlockFluidBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockFluidBase.class, remap = false)
public class MixinBlockFluidBase {

    @ModifyExpressionValue(method = "getFlowDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/material/Material;isLiquid()Z"), remap = true)
    private static boolean angelica$isFluidLiquid(boolean original, @Local Block block) {
        return block instanceof BlockFluidBase && original;
    }

}
