package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLiquid.class)
public class MixinBlockLiquid {

    @Inject(method = "getFlowDirection", at = @At("HEAD"), cancellable = true)
    private static void angelica$dispatchFlowVectorOnBlock(IBlockAccess world, int x, int y, int z, Material material, CallbackInfoReturnable<Double> cir) {
        final Block block = world.getBlock(x, y, z);
        if (!(block instanceof BlockLiquid)) {
            return;
        }
        final Vec3 vec = ((BlockLiquidFlowInvoker) block).angelica$getFlowVector(world, x, y, z);
        cir.setReturnValue(vec.xCoord == 0.0D && vec.zCoord == 0.0D
            ? -1000.0D
            : Math.atan2(vec.zCoord, vec.xCoord) - (Math.PI / 2.0D));
    }
}
