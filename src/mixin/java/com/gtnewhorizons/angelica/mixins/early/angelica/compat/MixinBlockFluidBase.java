package com.gtnewhorizons.angelica.mixins.early.angelica.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fluids.BlockFluidBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockFluidBase.class, remap = false)
public class MixinBlockFluidBase {

    @WrapOperation(method = "getFlowDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/material/Material;isLiquid()Z"), remap = true)
    private static boolean isLiquid(Material instance, Operation<Boolean> original, @Local Block block) {
        if (block instanceof BlockFluidBase) {
            return original.call(instance);
        } else {
            return false;
        }
    }

}
