/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TintGetterOverrideFast implements BlockAndTintGetter
{
	LevelReader parent;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public TintGetterOverrideFast(LevelReader parent) { this.parent = parent; }
	
	
	
	//=========//
	// methods //
	//=========//
	
	private Biome _getBiome(BlockPos pos)
	{
		#if MC_VER >= MC_1_18_2
		return this.parent.getBiome(pos).value();
		#else
		return parent.getBiome(pos);
		#endif
	}
	
	@Override
	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver) { return colorResolver.getColor(this._getBiome(blockPos), blockPos.getX(), blockPos.getZ()); }
	
	@Override
	public float getShade(Direction direction, boolean bl) { return this.parent.getShade(direction, bl); }
	
	@Override
	public LevelLightEngine getLightEngine() { return this.parent.getLightEngine(); }
	
	@Override
	public int getBrightness(LightLayer lightLayer, BlockPos blockPos) { return this.parent.getBrightness(lightLayer, blockPos); }
	
	@Override
	public int getRawBrightness(BlockPos blockPos, int i) { return this.parent.getRawBrightness(blockPos, i); }
	
	@Override
	public boolean canSeeSky(BlockPos blockPos) { return this.parent.canSeeSky(blockPos); }
	
	@Override
	@Nullable
	public BlockEntity getBlockEntity(BlockPos blockPos) { return this.parent.getBlockEntity(blockPos); }
	
	
	@Override
	public BlockState getBlockState(BlockPos blockPos) { return this.parent.getBlockState(blockPos); }
	
	@Override
	public FluidState getFluidState(BlockPos blockPos) { return this.parent.getFluidState(blockPos); }
	
	@Override
	public int getLightEmission(BlockPos blockPos) { return this.parent.getLightEmission(blockPos); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMaxLightLevel() { return parent.getMaxLightLevel(); }
	#else
	#endif
	
	@Override
	public Stream<BlockState> getBlockStates(AABB aABB)
	{ return this.parent.getBlockStates(aABB); }
	
	@Override
	public BlockHitResult clip(ClipContext clipContext)
	{ return this.parent.clip(clipContext); }
	
	@Override
	@Nullable
	public BlockHitResult clipWithInteractionOverride(Vec3 vec3, Vec3 vec32, BlockPos blockPos, VoxelShape voxelShape, BlockState blockState)
	{ return this.parent.clipWithInteractionOverride(vec3, vec32, blockPos, voxelShape, blockState); }
	
	@Override
	public double getBlockFloorHeight(VoxelShape voxelShape, Supplier<VoxelShape> supplier)
	{ return this.parent.getBlockFloorHeight(voxelShape, supplier); }
	
	@Override
	public double getBlockFloorHeight(BlockPos blockPos) { return this.parent.getBlockFloorHeight(blockPos); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMaxBuildHeight() { return this.parent.getMaxBuildHeight(); }
	#else
	@Override
	public int getMaxY() { return this.parent.getMaxY(); }
	#endif
	
	
	
	//==============//
	// post MC 1.17 //
	//==============//
	
	#if MC_VER >= MC_1_17_1
	@Override
	public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos blockPos, BlockEntityType<T> blockEntityType)
	{ return this.parent.getBlockEntity(blockPos, blockEntityType); }
	
	@Override
	public BlockHitResult isBlockInLine(ClipBlockStateContext clipBlockStateContext)
	{ return this.parent.isBlockInLine(clipBlockStateContext); }
	
	@Override
	public int getHeight() { return this.parent.getHeight(); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMinBuildHeight() { return this.parent.getMinBuildHeight(); }
	#else
	@Override
	public int getMinY() { return this.parent.getMinY(); }
	#endif
	
	@Override
	public int getSectionsCount() { return this.parent.getSectionsCount(); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMinSection() { return this.parent.getMinSection(); }
	#else
	@Override
	public int getMinSectionY() { return BlockAndTintGetter.super.getMinSectionY(); }	
	#endif
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMaxSection() { return this.parent.getMaxSection(); }
	#else
	@Override
	public int getMaxSectionY() { return this.parent.getMaxSectionY(); }
	#endif
	
	@Override
	public boolean isOutsideBuildHeight(BlockPos blockPos) { return this.parent.isOutsideBuildHeight(blockPos); }
	
	@Override
	public boolean isOutsideBuildHeight(int i) { return this.parent.isOutsideBuildHeight(i); }
	
	@Override
	public int getSectionIndex(int i) { return this.parent.getSectionIndex(i); }
	
	@Override
	public int getSectionIndexFromSectionY(int i) { return this.parent.getSectionIndexFromSectionY(i); }
	
	@Override
	public int getSectionYFromSectionIndex(int i) { return this.parent.getSectionYFromSectionIndex(i); }
    #endif
}
