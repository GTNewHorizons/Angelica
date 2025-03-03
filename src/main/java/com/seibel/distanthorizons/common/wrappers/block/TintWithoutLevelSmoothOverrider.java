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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#endif

public class TintWithoutLevelSmoothOverrider implements BlockAndTintGetter
{
	final BiomeWrapper biome;
	public int smoothingRange;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public TintWithoutLevelSmoothOverrider(BiomeWrapper biome, int smoothingRange)
	{
		this.biome = biome;
		this.smoothingRange = smoothingRange;
	}
	
	
	
	//=========//
	// methods //
	//=========//
	
	@Override
	public int getBlockTint(BlockPos blockPos, ColorResolver colorResolver)
	{
		return colorResolver.getColor(_unwrap(biome.biome), blockPos.getX(), blockPos.getZ());
	}
	private Biome _unwrap(#if MC_VER >= MC_1_18_2 Holder<Biome> #else Biome #endif biome)
	{
		#if MC_VER >= MC_1_18_2
		return biome.value();
		#else
		return biome;
		#endif
	}

//    public int calculateBlockTint(BlockPos blockPos, ColorResolver colorResolver)
//    {
//        int i = smoothingRange;
//        if (i == 0)
//            return colorResolver.getColor(_getBiome(blockPos), blockPos.getX(), blockPos.getZ());
//        int j = (i * 2 + 1) * (i * 2 + 1);
//        int k = 0;
//        int l = 0;
//        int m = 0;
//        Cursor3D cursor3D = new Cursor3D(blockPos.getX() - i, blockPos.getY(), blockPos.getZ() - i, blockPos.getX() + i, blockPos.getY(), blockPos.getZ() + i);
//        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
//        while (cursor3D.advance())
//        {
//            mutableBlockPos.set(cursor3D.nextX(), cursor3D.nextY(), cursor3D.nextZ());
//            int n;
//            if (LodCommonMain.forgeMethodCaller != null) {
//                n = LodCommonMain.forgeMethodCaller.colorResolverGetColor(colorResolver, _getBiome(mutableBlockPos),
//                        mutableBlockPos.getX(), mutableBlockPos.getZ());
//            } else {
//                n = colorResolver.getColor(_getBiome(mutableBlockPos), mutableBlockPos.getX(), mutableBlockPos.getZ());
//            }
//
//            k += (n & 0xFF0000) >> 16;
//            l += (n & 0xFF00) >> 8;
//            m += n & 0xFF;
//        }
//        return (k / j & 0xFF) << 16 | (l / j & 0xFF) << 8 | m / j & 0xFF;
//    }
	
	@Override
	public float getShade(Direction direction, boolean shade)
	{ throw new UnsupportedOperationException("ERROR: getShade() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	@Override
	public LevelLightEngine getLightEngine()
	{ throw new UnsupportedOperationException("ERROR: getLightEngine() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	@Nullable
	@Override
	public BlockEntity getBlockEntity(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getBlockEntity() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	
	@Override
	public BlockState getBlockState(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getBlockState() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	@Override
	public FluidState getFluidState(BlockPos pos)
	{ throw new UnsupportedOperationException("ERROR: getFluidState() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	
	
	//==============//
	// post MC 1.17 //
	//==============//
	
	#if MC_VER >= MC_1_17_1
	
	@Override
	public int getHeight()
	{ throw new UnsupportedOperationException("ERROR: getHeight() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	
	#if MC_VER < MC_1_21_3
	@Override
	public int getMinBuildHeight() 
	{ throw new UnsupportedOperationException("ERROR: getMinBuildHeight() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	#else
	@Override
	public int getMinY()
	{ throw new UnsupportedOperationException("ERROR: getMinY() called on TintWithoutLevelSmoothOverrider. Object is for tinting only."); }
	#endif
	
	#endif
	
}
