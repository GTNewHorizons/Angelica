package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.mixins.hooks.FluidHooks;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.RenderBlockFluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.sugar.Local;

@Mixin(value = RenderBlockFluid.class, remap = false)
public class MixinRenderBlockFluid {

    @Redirect(
        method = "renderWorldBlock", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraftforge/fluids/BlockFluidBase;getFlowDirection(Lnet/minecraft/world/IBlockAccess;III)D"))
    double hodgepodge$directFlowDirectionCheck(IBlockAccess world, int x, int y, int z, @Local BlockFluidBase block) {
        return FluidHooks.getFlowDirection(world, x, y, z, block);
    }
}
