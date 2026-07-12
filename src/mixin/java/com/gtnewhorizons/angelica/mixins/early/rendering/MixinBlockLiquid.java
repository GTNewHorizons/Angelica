package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockLiquid.class)
public class MixinBlockLiquid {

    @Redirect(
        method = "getFlowDirection",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockLiquid;getFlowVector(Lnet/minecraft/world/IBlockAccess;III)Lnet/minecraft/util/Vec3;"))
    private static Vec3 angelica$flowVectorOfActualBlock(BlockLiquid singleton, IBlockAccess world, int x, int y, int z) {
        final Block block = world.getBlock(x, y, z);
        final BlockLiquid liquid = block instanceof BlockLiquid ? (BlockLiquid) block : singleton;
        return ((BlockLiquidFlowInvoker) liquid).angelica$getFlowVector(world, x, y, z);
    }
}
