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

package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import net.minecraft.world.level.lighting.*;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;

public class DummyLightEngine extends LevelLightEngine
{
	
	public DummyLightEngine(LightGetterAdaptor genRegion)
	{
		super(genRegion, false, false);
	}
	
	
	#if MC_VER < MC_1_20_1
	@Override
	public void onBlockEmissionIncrease(BlockPos blockPos, int i) { }
	
	@Override
	public int runUpdates(int i, boolean bl, boolean bl2) { return 0; }
	
	@Override
	public void enableLightSources(ChunkPos chunkPos, boolean bl) { }

	#else
	@Override
	public int runLightUpdates() { return 0; }
	
	@Override
	public void setLightEnabled(ChunkPos $$0, boolean $$1) { }
	
	@Override
	public void propagateLightSources(ChunkPos arg) { }
	
	public boolean lightOnInSection(SectionPos $$0) { return false; }
    #endif
	
	@Override
	public void queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer #if MC_VER < MC_1_20_1 , boolean bl #endif ) { }
	
	@Override
	public void checkBlock(BlockPos blockPos) { }
	
	@Override
	public boolean hasLightWork() { return false; }
	
	@Override
	public void updateSectionStatus(SectionPos sectionPos, boolean bl) { }
	
	@Override
	public LayerLightEventListener getLayerListener(LightLayer lightLayer) { return LayerLightEventListener.DummyLightLayerEventListener.INSTANCE; }
	
	@Override
	public int getRawBrightness(BlockPos blockPos, int i) { return 0; }
	
	public void lightChunk(ChunkAccess chunkAccess, boolean needLightBlockUpdate) { }
	
	@Override
	public String getDebugData(LightLayer lightLayer, SectionPos sectionPos) { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public void retainData(ChunkPos chunkPos, boolean bl) { }
	
	#if MC_VER >= MC_1_17_1
	@Override
	public int getLightSectionCount() { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public int getMinLightSection() { throw new UnsupportedOperationException("This should never be used!"); }
	@Override
	public int getMaxLightSection() { throw new UnsupportedOperationException("This should never be used!"); }
    #endif
	
}