package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.compat.forge.IForgeBlockState;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3d;

public class BlockState implements IForgeBlockState {
    private final static Vector3d ZERO = new Vector3d(0, 0, 0);
    private final Block block;
    private final int meta;

    public BlockState(Block block, int meta) {
        this.block = block;
        this.meta = meta;
    }
    public Block getBlock() {
        return block;
    }

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

    public boolean isOpaqueFullCube(BlockRenderView world, BlockPos pos) {
        return true;
    }

    public boolean isFullCube(BlockRenderView world, BlockPos pos) { return true; }

    public Vector3d getModelOffset(BlockRenderView world, BlockPos pos) {
        return ZERO;
    }

    public boolean isAir() {
        return(block != null && block.isAir( null, 0, 0, 0));
    }

    public BlockRenderType getRenderType() {
        return BlockRenderType.MODEL;
    }

    public boolean hasTileEntity() {
        return block.hasTileEntity(meta);
    }

    public long getRenderingSeed(BlockPos.Mutable pos) {
        return 0;
    }

    public FluidState getFluidState() {
        return null;
    }

    public boolean shouldDisplayFluidOverlay(BlockRenderView world, BlockPos adjPos, FluidState fluidState) {
        return true;
    }

    public Material getMaterial() {
        return block.getMaterial();
    }

    public boolean isSideSolid(BlockRenderView world, BlockPos pos, ForgeDirection dir, SideShapeType sideShapeType) {
        return true;
    }

    public VoxelShape getCullingShape(BlockRenderView world, BlockPos pos) {
        return null;
    }
}
