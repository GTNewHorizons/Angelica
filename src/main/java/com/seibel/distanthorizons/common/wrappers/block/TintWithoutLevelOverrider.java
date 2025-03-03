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

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.ColorUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#endif

public class TintWithoutLevelOverrider implements BlockAndTintGetter
{
	/** 
	 * This will only ever be null if there was an issue with {@link IClientLevelWrapper#getPlainsBiomeWrapper()}
	 * but {@link Nullable} is there just in case. 
	 */
	@Nullable
	private final Biome biome;
	
	
	
	public TintWithoutLevelOverrider(BiomeWrapper biomeWrapper, IClientLevelWrapper clientLevelWrapper)
	{
		// try to get the wrapped biome
		Biome unwrappedBiome = null;
		if (biomeWrapper.biome != null)
		{
			unwrappedBiome = unwrap(biomeWrapper.biome);
		}
		
		if(unwrappedBiome == null)
		{
			// we are looking at the empty biome wrapper, try using plains as a backup
			BiomeWrapper plainsBiomeWrapper = ((BiomeWrapper) clientLevelWrapper.getPlainsBiomeWrapper());
			if (plainsBiomeWrapper != null)
			{
				unwrappedBiome = unwrap(plainsBiomeWrapper.biome);
			}
		}
		
		this.biome = unwrappedBiome;
	}
	
	
	
	@Override
	public int getBlockTint(@NotNull BlockPos blockPos, @NotNull ColorResolver colorResolver) 
	{ 
		if (this.biome != null)
		{
			return colorResolver.getColor(this.biome, blockPos.getX(), blockPos.getZ());
		}
		else
		{
			// hopefully unneeded debug color
			return ColorUtil.CYAN;
		}
	}
	
	private static Biome unwrap(#if MC_VER >= MC_1_18_2 Holder<Biome> #else Biome #endif biome)
	{
		#if MC_VER >= MC_1_18_2
		return biome.value();
		#else
		return biome;
		#endif
	}
	
	
	
	//================//
	// unused methods //
	//================//
	
	@Override
	public float getShade(@NotNull Direction direction, boolean shade)
	{
		throw new UnsupportedOperationException("ERROR: getShade() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Override
	public @NotNull LevelLightEngine getLightEngine()
	{
		throw new UnsupportedOperationException("ERROR: getLightEngine() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Nullable
	@Override
	public BlockEntity getBlockEntity(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getBlockEntity() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	
	@Override
	public @NotNull BlockState getBlockState(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getBlockState() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	@Override
	public @NotNull FluidState getFluidState(@NotNull BlockPos pos)
	{
		throw new UnsupportedOperationException("ERROR: getFluidState() called on TintWithoutLevelOverrider. Object is for tinting only.");
	}
	
	
	
	//==============//
	// post MC 1.17 //
	//==============//
	
	#if MC_VER >= MC_1_17_1
	
	@Override
	public int getHeight()
	{ throw new UnsupportedOperationException("ERROR: getHeight() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMinBuildHeight() 
	{ throw new UnsupportedOperationException("ERROR: getMinBuildHeight() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	#else
	@Override
	public int getMinY()
	{ throw new UnsupportedOperationException("ERROR: getMinY() called on TintWithoutLevelOverrider. Object is for tinting only."); }
	#endif
	
	#endif
	
}
