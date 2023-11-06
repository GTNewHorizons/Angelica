package me.jellysquid.mods.sodium.client.world;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import com.gtnewhorizons.angelica.compat.mojang.ColorResolver;
import com.gtnewhorizons.angelica.compat.mojang.FluidState;
import com.gtnewhorizons.angelica.compat.mojang.LightType;
import com.gtnewhorizons.angelica.compat.mojang.LightingProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.util.ForgeDirection;

import javax.annotation.Nullable;

/**
 * Wrapper object used to defeat identity comparisons in mods. Since vanilla provides a unique object to them for each
 * subchunk, we do the same.
 */
public class WorldSliceLocal implements BlockRenderView {
    private final BlockRenderView view;

    public WorldSliceLocal(BlockRenderView view) {
        this.view = view;
    }

    @Override
    public float getBrightness(ForgeDirection direction, boolean shaded) {
        return view.getBrightness(direction, shaded);
    }

    @Override
    public LightingProvider getLightingProvider() {
        return view.getLightingProvider();
    }

    @Override
    public int getColor(BlockPos pos, ColorResolver colorResolver) {
        return view.getColor(pos, colorResolver);
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos) {
        return view.getLightLevel(type, pos);
    }

    @Override
    public int getBaseLightLevel(BlockPos pos, int ambientDarkness) {
        return view.getBaseLightLevel(pos, ambientDarkness);
    }

    @Override
    public boolean isSkyVisible(BlockPos pos) {
        return view.isSkyVisible(pos);
    }

    @Override
    public BiomeGenBase getBiomeForNoiseGen(int x, int y, int z) {
        return null;
    }

    @Override
    @Nullable
    public TileEntity getBlockEntity(BlockPos pos) {
        return view.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return view.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return view.getFluidState(pos);
    }

//    @Override
    public int getLuminance(BlockPos pos) {
        return 0;
//        return view.getLuminance(pos);
    }

//    @Override
    public int getMaxLightLevel() {
        return 15;
//        return view.getMaxLightLevel();
    }

//    @Override
    public int getHeight() {
        return 255;
//        return view.getHeight();
    }

//    @Override
//    public Stream<BlockState> method_29546(Box arg) {
//        return view.method_29546(arg);
//    }
//
//    @Override
//    public BlockHitResult raycast(RaycastContext context) {
//        return view.raycast(context);
//    }
//
//    @Override
//    @Nullable
//    public BlockHitResult raycastBlock(Vector3d start, Vector3d end, BlockPos pos, VoxelShape shape, BlockState state) {
//        return view.raycastBlock(start, end, pos, shape, state);
//    }
//
//    @Override
//    public double getDismountHeight(VoxelShape blockCollisionShape, Supplier<VoxelShape> belowBlockCollisionShapeGetter) {
//        return view.getDismountHeight(blockCollisionShape, belowBlockCollisionShapeGetter);
//    }
//
//    @Override
//    public double getDismountHeight(BlockPos pos) {
//        return view.getDismountHeight(pos);
//    }
//
//    public static <T> T raycast(RaycastContext arg, BiFunction<RaycastContext, BlockPos, T> context, Function<RaycastContext, T> blockRaycaster) {
//        return BlockView.raycast(arg, context, blockRaycaster);
//    }
}
