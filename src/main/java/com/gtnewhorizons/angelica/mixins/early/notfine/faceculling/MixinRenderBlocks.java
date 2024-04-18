package com.gtnewhorizons.angelica.mixins.early.notfine.faceculling;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RenderBlocks.class)
public abstract class MixinRenderBlocks {

    /**
     * @author jss2a98aj
     * @reason Culling a few more faces
     */
    @Overwrite
    public boolean renderBlockFence(BlockFence fence, int x, int y, int z) {
        float min = 0.375F;
        float max = 0.625F;
        setRenderBounds(min, 0.0D, min, max, 1.0D, max);
        renderStandardBlock(fence, x, y, z);

        boolean connectXNeg = fence.canConnectFenceTo(blockAccess, x - 1, y, z);
        boolean connectXPos = fence.canConnectFenceTo(blockAccess, x + 1, y, z);
        boolean connectZNeg = fence.canConnectFenceTo(blockAccess, x, y, z - 1);
        boolean connectZPos = fence.canConnectFenceTo(blockAccess, x, y, z + 1);
        boolean connectX = connectXNeg || connectXPos;
        boolean connectZ = connectZNeg || connectZPos;

        min = 0.4375F;
        max = 0.5625F;
        float minY = 0.75F;
        float maxY = 0.9375F;
        float minX = connectXNeg ? 0.0F : min;
        float maxX = connectXPos ? 1.0F : max;
        float minZ = connectZNeg ? 0.0F : min;
        float maxZ = connectZPos ? 1.0F : max;
        field_152631_f = true;
        //Upper beam
        if(connectX) {
            setRenderBounds(minX, minY, min, maxX, maxY, max);
            renderStandardBlock(fence, x, y, z);
        }
        if(connectZ) {
            setRenderBounds(min, minY, minZ, max, maxY, maxZ);
            renderStandardBlock(fence, x, y, z);
        }

        minY = 0.375F;
        maxY = 0.5625F;
        //Lower beam
        if(connectX) {
            setRenderBounds(minX, minY, min, maxX, maxY, max);
            renderStandardBlock(fence, x, y, z);
        }
        if(connectZ) {
            setRenderBounds(min, minY, minZ, max, maxY, maxZ);
            renderStandardBlock(fence, x, y, z);
        }

        field_152631_f = false;
        fence.setBlockBoundsBasedOnState(blockAccess, x, y, z);
        return true;
    }

    /**
     * @author jss2a98aj
     * @reason Fix modded wall detection
     */
    @Redirect(
        method = "renderBlockFenceGate(Lnet/minecraft/block/BlockFenceGate;III)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getBlock(III)Lnet/minecraft/block/Block;"
        ),
        expect = 4
    )
    Block detectModdedWalls(IBlockAccess world, int x, int y, int z) {
        return world.getBlock(x, y, z) instanceof BlockWall ? Blocks.cobblestone_wall : null;
    }

    @Shadow public IBlockAccess blockAccess;
    @Shadow public boolean field_152631_f;
    @Shadow public abstract void setRenderBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    @Shadow public abstract boolean renderStandardBlock(Block blockType, int blockX, int blockY, int blockZ);

}
