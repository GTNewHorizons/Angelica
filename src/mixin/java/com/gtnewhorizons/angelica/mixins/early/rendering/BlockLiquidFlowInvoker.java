package com.gtnewhorizons.angelica.mixins.early.rendering;

import net.minecraft.block.BlockLiquid;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockLiquid.class)
public interface BlockLiquidFlowInvoker {

    @Invoker("getFlowVector")
    Vec3 angelica$getFlowVector(IBlockAccess world, int x, int y, int z);
}
