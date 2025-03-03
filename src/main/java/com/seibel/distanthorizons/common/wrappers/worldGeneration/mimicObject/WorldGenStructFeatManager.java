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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldGenSettings;
#if MC_VER < MC_1_19_2
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.StructureFeatureManager;
#else
#if MC_VER >= MC_1_19_4
import net.minecraft.world.level.levelgen.WorldOptions;
#endif
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.StructureManager;
#endif
#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.levelgen.structure.StructureCheck;
#endif

import net.minecraft.world.level.levelgen.structure.StructureStart;

#if MC_VER < MC_1_18_2
import net.minecraft.world.level.levelgen.feature.StructureFeature;
#endif

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif



public class WorldGenStructFeatManager extends #if MC_VER < MC_1_19_2 StructureFeatureManager #else StructureManager #endif
{
	final WorldGenLevel genLevel;
	
	#if MC_VER < MC_1_19_4
	WorldGenSettings worldGenSettings;
	#else
	WorldOptions worldOptions;
	#endif
	
	#if MC_VER >= MC_1_18_2
	StructureCheck structureCheck;
	#endif
	
	#if MC_VER < MC_1_19_4
	public WorldGenStructFeatManager(
			WorldGenSettings worldGenSettings,
			WorldGenLevel genLevel #if MC_VER >= MC_1_18_2 , StructureCheck structureCheck #endif )
	{
		
		super(genLevel, worldGenSettings #if MC_VER >= MC_1_18_2 , structureCheck #endif );
		this.genLevel = genLevel;
		this.worldGenSettings = worldGenSettings;
	}
	#else
	public WorldGenStructFeatManager(
			WorldOptions worldOptions,
			WorldGenLevel genLevel, StructureCheck structureCheck)
	{
		
		super(genLevel, worldOptions, structureCheck);
		this.genLevel = genLevel;
		this.worldOptions = worldOptions;
	}
	#endif
	
	@Override
	public WorldGenStructFeatManager forWorldGenRegion(WorldGenRegion worldGenRegion)
	{
		if (worldGenRegion == genLevel)
			return this;
	#if MC_VER < MC_1_19_4
		return new WorldGenStructFeatManager(worldGenSettings, worldGenRegion #if MC_VER >= MC_1_18_2 , structureCheck #endif );
	#else
		return new WorldGenStructFeatManager(worldOptions, worldGenRegion, structureCheck);
	#endif
	}
	
	private ChunkAccess _getChunk(int x, int z, ChunkStatus status)
	{
		if (genLevel == null) return null;
		return genLevel.getChunk(x, z, status, false);
	}
	
	#if MC_VER < MC_1_18_2
	@Override
	public Stream<? extends StructureStart<?>> startsForFeature(
			SectionPos sectionPos2,
			StructureFeature<?> structureFeature)
	{
		ChunkAccess chunk = _getChunk(sectionPos2.x(), sectionPos2.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return Stream.empty();
		
		// FIXME getReferencesForFeature can throw ConcurrentModificationException's
		return chunk.getReferencesForFeature(structureFeature).stream().map(pos -> {
			SectionPos sectPos = SectionPos.of(ChunkPos.getX(pos), 0, ChunkPos.getZ(pos));
			ChunkAccess startChunk = _getChunk(sectPos.x(), sectPos.z(), ChunkStatus.STRUCTURE_STARTS);
			if (startChunk == null) return null;
			return this.getStartForFeature(sectPos, structureFeature, startChunk);
		}).filter(structureStart -> structureStart != null && structureStart.isValid());
	}
	#else
	@Override
	public boolean hasAnyStructureAt(BlockPos blockPos)
	{
		SectionPos sectionPos = SectionPos.of(blockPos);
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return false;
		return chunk.hasAnyStructureReferences();
	}
	
	#if MC_VER == MC_1_18_1
	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<? extends StructureStart<?>> startsForFeature(SectionPos sectionPos,
															  StructureFeature<?> structureFeature) {

		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return List.of();

		// Copied from StructureFeatureManager::startsForFeature(...) with slight tweaks
		LongSet longSet = chunk.getReferencesForFeature(structureFeature);
		ImmutableList.Builder builder = ImmutableList.builder();
		LongIterator longIterator = longSet.iterator();
		while (longIterator.hasNext()) {
			long l = (Long)longIterator.next();
			SectionPos sectPos = SectionPos.of(new ChunkPos(l), genLevel.getMinSection());
			ChunkAccess startChunk = _getChunk(sectPos.x(), sectPos.z(), ChunkStatus.STRUCTURE_STARTS);
			if (startChunk == null) continue;
			StructureStart<?> structureStart = this.getStartForFeature(sectPos, structureFeature, startChunk);
			if (structureStart == null || !structureStart.isValid()) continue;
			builder.add(structureStart);
		}
		return builder.build();
	}
	#else
	#if MC_VER < MC_1_19_2
	@Override
	public List<StructureStart> startsForFeature(SectionPos sectionPos, Predicate<ConfiguredStructureFeature<?, ?>> predicate)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return List.of();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		Map<ConfiguredStructureFeature<?, ?>, LongSet> map = chunk.getAllReferences();
		
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Iterator<Map.Entry<ConfiguredStructureFeature<?, ?>, LongSet>> var5 = map.entrySet().iterator();
		
		while (var5.hasNext())
		{
			Map.Entry<ConfiguredStructureFeature<?, ?>, LongSet> entry = var5.next();
			ConfiguredStructureFeature<?, ?> configuredStructureFeature = entry.getKey();
			if (predicate.test(configuredStructureFeature))
			{
				LongSet var10002 = (LongSet) entry.getValue();
				Objects.requireNonNull(builder);
				this.fillStartsForFeature(configuredStructureFeature, var10002, builder::add);
			}
		}
		
		return builder.build();
	}
	
	@Override
	public List<StructureStart> startsForFeature(SectionPos sectionPos, ConfiguredStructureFeature<?, ?> configuredStructureFeature)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (List<StructureStart>) Stream.empty();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		LongSet longSet = chunk.getReferencesForFeature(configuredStructureFeature);
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Objects.requireNonNull(builder);
		this.fillStartsForFeature(configuredStructureFeature, longSet, builder::add);
		return builder.build();
	}
	
	@Override
	public Map<ConfiguredStructureFeature<?, ?>, LongSet> getAllStructuresAt(BlockPos blockPos)
	{
		SectionPos sectionPos = SectionPos.of(blockPos);
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (Map<ConfiguredStructureFeature<?, ?>, LongSet>) Stream.empty();
		return chunk.getAllReferences();
	}
	#else
	@Override
	public List<StructureStart> startsForStructure(ChunkPos sectionPos, Predicate<Structure> predicate)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x, sectionPos.z, ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return List.of();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		Map<Structure, LongSet> map = chunk.getAllReferences();
		
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Iterator<Map.Entry<Structure, LongSet>> var5 = map.entrySet().iterator();
		
		while (var5.hasNext())
		{
			Map.Entry<Structure, LongSet> entry = var5.next();
			Structure configuredStructureFeature = entry.getKey();
			if (predicate.test(configuredStructureFeature))
			{
				LongSet var10002 = (LongSet) entry.getValue();
				Objects.requireNonNull(builder);
				this.fillStartsForStructure(configuredStructureFeature, var10002, builder::add);
			}
		}
		
		return builder.build();
	}
	
	@Override
	public List<StructureStart> startsForStructure(SectionPos sectionPos, Structure structure)
	{
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (List<StructureStart>) Stream.empty();
		
		// Copied from StructureFeatureManager::startsForFeature(...)
		LongSet longSet = chunk.getReferencesForStructure(structure);
		ImmutableList.Builder<StructureStart> builder = ImmutableList.builder();
		Objects.requireNonNull(builder);
		this.fillStartsForStructure(structure, longSet, builder::add);
		return builder.build();
	}
	
	@Override
	public Map<Structure, LongSet> getAllStructuresAt(BlockPos blockPos)
	{
		SectionPos sectionPos = SectionPos.of(blockPos);
		ChunkAccess chunk = _getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES);
		if (chunk == null) return (Map<Structure, LongSet>) Stream.empty();
		return chunk.getAllReferences();
	}
	#endif
	#endif
	#endif
}
