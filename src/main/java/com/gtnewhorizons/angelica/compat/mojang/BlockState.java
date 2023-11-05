package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.compat.forge.IForgeBlockState;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;

public class BlockState implements IForgeBlockState {

    public boolean isSideInvisible(BlockState adjState, ForgeDirection facing) {
        return false;
    }

    public boolean isOpaque() {
        return true;
    }

    public VoxelShape getCullingFace(BlockView view, BlockPos pos, ForgeDirection facing) {
        return null;
    }

    public int getLightValue(BlockRenderView world, BlockPos pos) { return 15;}

    public float getAmbientOcclusionLightLevel(BlockRenderView world, BlockPos pos) { return 1.0f; }

    public boolean hasEmissiveLighting(BlockRenderView world, BlockPos pos) {
        return false;
    }

    public boolean shouldBlockVision(BlockRenderView world, BlockPos pos) { return true;}

    public int getOpacity(BlockRenderView world, BlockPos pos) {
        return 15;
    }

    public boolean isOpaqueFullCube(World world, BlockPos pos) {
        return true;
    }

    public boolean isFullCube(BlockRenderView world, BlockPos pos) { return true; }

    public Vector3d getModelOffset(BlockRenderView world, BlockPos pos) {
        return null;
    }

    public boolean isAir() {
        return false;
    }
}
