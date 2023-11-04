package me.jellysquid.mods.sodium.client.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.level.ColorResolver;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
    public float getBrightness(Direction direction, boolean shaded) {
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
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
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

    @Override
    public int getLuminance(BlockPos pos) {
        return view.getLuminance(pos);
    }

    @Override
    public int getMaxLightLevel() {
        return view.getMaxLightLevel();
    }

    @Override
    public int getHeight() {
        return view.getHeight();
    }

    @Override
    public Stream<BlockState> method_29546(Box arg) {
        return view.method_29546(arg);
    }

    @Override
    public BlockHitResult raycast(RaycastContext context) {
        return view.raycast(context);
    }

    @Override
    @Nullable
    public BlockHitResult raycastBlock(Vec3d start, Vec3d end, BlockPos pos, VoxelShape shape, BlockState state) {
        return view.raycastBlock(start, end, pos, shape, state);
    }

    @Override
    public double getDismountHeight(VoxelShape blockCollisionShape, Supplier<VoxelShape> belowBlockCollisionShapeGetter) {
        return view.getDismountHeight(blockCollisionShape, belowBlockCollisionShapeGetter);
    }

    @Override
    public double getDismountHeight(BlockPos pos) {
        return view.getDismountHeight(pos);
    }

    public static <T> T raycast(RaycastContext arg, BiFunction<RaycastContext, BlockPos, T> context, Function<RaycastContext, T> blockRaycaster) {
        return BlockView.raycast(arg, context, blockRaycaster);
    }
}
