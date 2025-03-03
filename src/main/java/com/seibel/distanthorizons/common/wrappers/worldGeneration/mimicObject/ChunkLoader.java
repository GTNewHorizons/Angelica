/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU GPL v3 License.
 *
 *    Copyright (C) 2020 James Seibel
 *
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, version 3.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.common.wrappers.worldGeneration.mimicObject;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.worldGeneration.BatchGenerationEnvironment;

import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.Registry;
#if MC_VER >= MC_1_19_4
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
#endif
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;

#if MC_VER < MC_1_21_3
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
#else
#endif

import net.minecraft.world.level.levelgen.Heightmap;
#if MC_VER >= MC_1_18_2
import net.minecraft.world.level.levelgen.blending.BlendingData;
#if MC_VER < MC_1_19_2
import net.minecraft.world.level.levelgen.feature.StructureFeature;
#endif
import net.minecraft.world.ticks.LevelChunkTicks;
#endif
#if MC_VER >= MC_1_18_2
import net.minecraft.core.Holder;
#if MC_VER < MC_1_19_2
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
#endif
#endif

#if MC_VER == MC_1_16_5 || MC_VER == MC_1_17_1
import net.minecraft.world.level.material.Fluids;
#endif

#if MC_VER == MC_1_20_6
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
#elif MC_VER >= MC_1_21_1
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
#endif

import net.minecraft.world.level.material.Fluid;


public class ChunkLoader
{
	private static boolean zeroChunkPosErrorLogged = false;
	
	#if MC_VER >= MC_1_19_2
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
	#elif MC_VER >= MC_1_18_2
	private static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codec(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
	#endif
	private static final String TAG_UPGRADE_DATA = "UpgradeData";
	private static final String BLOCK_TICKS_TAG_18 = "block_ticks";
	private static final String FLUID_TICKS_TAG_18 = "fluid_ticks";
	private static final String BLOCK_TICKS_TAG_PRE18 = "TileTicks";
	private static final String FLUID_TICKS_TAG_PRE18 = "LiquidTicks";
	private static final ConfigBasedLogger LOGGER = BatchGenerationEnvironment.LOAD_LOGGER;
	
	private static boolean lightingSectionErrorLogged = false;
	
	private static final ConcurrentHashMap<String, Object> LOGGED_ERROR_MESSAGE_MAP = new ConcurrentHashMap<>();
	
	
	
	//============//
	// read chunk //
	//============//
	
	public static LevelChunk read(WorldGenLevel level, ChunkPos chunkPos, CompoundTag chunkData)
	{
		#if MC_VER < MC_1_18_2
		CompoundTag tagLevel = chunkData.getCompound("Level");
		#else
		CompoundTag tagLevel = chunkData;
		#endif
		
		ChunkPos actualPos = new ChunkPos(tagLevel.getInt("xPos"), tagLevel.getInt("zPos"));
		if (!Objects.equals(chunkPos, actualPos))
		{
			#if MC_VER >= MC_1_18_2
			if (actualPos.equals(ChunkPos.ZERO))
			#else
			if (actualPos.equals(ChunkPos.INVALID_CHUNK_POS))
			#endif
			{
				if (!zeroChunkPosErrorLogged)
				{
					zeroChunkPosErrorLogged = true;
					
					// explicit chunkPos toString is necessary otherwise the JDK 17 compiler breaks
					LOGGER.warn("Chunk file at ["+chunkPos.toString()+"] doesn't have a chunk pos. \n" +
						"This might happen if the world was created using an external program. \n" +
						"DH will attempt to parse the chunk anyway and won't log this message again.\n" +
						"If issues arise please try optimizing your world to fix this issue. \n" +
						"World optimization can be done from the singleplayer world selection screen."+
						"");
				}
			}
			else
			{
				// everything is on one line to fix a JDK 17 compiler issue
				// if the issue is ever resolved, feel free to make this multi-line for readability
				LOGGER.error("Chunk file at ["+chunkPos.toString()+"] is in the wrong location. \nPlease try optimizing your world to fix this issue. \nWorld optimization can be done from the singleplayer world selection screen. \n(Expected pos: ["+chunkPos.toString()+"], actual ["+actualPos.toString()+"])");
				return null;
			}
		}
		
		#if MC_VER < MC_1_20_6
		ChunkStatus.ChunkType chunkType;
		#else
		ChunkType chunkType;
		#endif
		chunkType = readChunkType(tagLevel);
		
		#if MC_VER < MC_1_18_2
			if (chunkType != ChunkStatus.ChunkType.LEVELCHUNK)
				return null;
		#else
			
			BlendingData blendingData = readBlendingData(tagLevel);
			#if MC_VER < MC_1_19_2
			if (chunkType == ChunkStatus.ChunkType.PROTOCHUNK && (blendingData == null || !blendingData.oldNoise()))
				return null;
			#else
			if (chunkType == #if MC_VER < MC_1_20_6 ChunkStatus.ChunkType.PROTOCHUNK #else ChunkType.PROTOCHUNK #endif && blendingData == null)
				return null;
			#endif
		
		#endif
		
		long inhabitedTime = tagLevel.getLong("InhabitedTime");
		
		//================== Read params for making the LevelChunk ==================
		UpgradeData upgradeData = tagLevel.contains(TAG_UPGRADE_DATA, 10)
				? new UpgradeData(tagLevel.getCompound(TAG_UPGRADE_DATA)#if MC_VER >= MC_1_17_1 , level #endif )
				: UpgradeData.EMPTY;
		
		boolean isLightOn = tagLevel.getBoolean("isLightOn");
		#if MC_VER < MC_1_18_2
		ChunkBiomeContainer chunkBiomeContainer = new ChunkBiomeContainer(
				level.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)#if MC_VER >= MC_1_17_1 , level #endif ,
				chunkPos, level.getLevel().getChunkSource().getGenerator().getBiomeSource(),
				tagLevel.contains("Biomes", 11) ? tagLevel.getIntArray("Biomes") : null);
		
		TickList<Block> blockTicks = tagLevel.contains(BLOCK_TICKS_TAG_PRE18, 9)
				? ChunkTickList.create(tagLevel.getList(BLOCK_TICKS_TAG_PRE18, 10), Registry.BLOCK::getKey, Registry.BLOCK::get)
				: new ProtoTickList<Block>(block -> (block == null || block.defaultBlockState().isAir()), chunkPos,
				tagLevel.getList("ToBeTicked", 9)#if MC_VER >= MC_1_17_1 , level #endif );
		
		TickList<Fluid> fluidTicks = tagLevel.contains(FLUID_TICKS_TAG_PRE18, 9)
				? ChunkTickList.create(tagLevel.getList(FLUID_TICKS_TAG_PRE18, 10), Registry.FLUID::getKey, Registry.FLUID::get)
				: new ProtoTickList<Fluid>(fluid -> (fluid == null || fluid == Fluids.EMPTY), chunkPos,
				tagLevel.getList("LiquidsToBeTicked", 9)#if MC_VER >= MC_1_17_1 , level #endif );
		#else
		#if MC_VER < MC_1_19_4
		LevelChunkTicks<Block> blockTicks = LevelChunkTicks.load(tagLevel.getList(BLOCK_TICKS_TAG_18, 10),
				string -> Registry.BLOCK.getOptional(ResourceLocation.tryParse(string)), chunkPos);
		LevelChunkTicks<Fluid> fluidTicks = LevelChunkTicks.load(tagLevel.getList(FLUID_TICKS_TAG_18, 10),
				string -> Registry.FLUID.getOptional(ResourceLocation.tryParse(string)), chunkPos);
		#else
		LevelChunkTicks<Block> blockTicks = LevelChunkTicks.load(tagLevel.getList(BLOCK_TICKS_TAG_18, 10),
				(string -> BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(string))), chunkPos);
		LevelChunkTicks<Fluid> fluidTicks = LevelChunkTicks.load(tagLevel.getList(FLUID_TICKS_TAG_18, 10),
				string -> BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(string)), chunkPos);
		#endif
		#endif
		
		LevelChunkSection[] levelChunkSections = readSections(level, chunkPos, tagLevel);
		
		// ====================== Make the chunk =========================
		#if MC_VER < MC_1_18_2
		LevelChunk chunk = new LevelChunk((Level) level.getLevel(), chunkPos, chunkBiomeContainer, upgradeData, blockTicks,
				fluidTicks, inhabitedTime, levelChunkSections, null);
		#else
		
		LevelChunk chunk = new LevelChunk((Level) level, chunkPos, upgradeData, blockTicks,
				fluidTicks, inhabitedTime, levelChunkSections, null, blendingData);
		#endif
		// Set some states after object creation
		chunk.setLightCorrect(isLightOn);
		readHeightmaps(chunk, chunkData);
		readPostPocessings(chunk, chunkData);
		return chunk;
	}
	private static LevelChunkSection[] readSections(LevelAccessor level, ChunkPos chunkPos, CompoundTag chunkData)
	{
		#if MC_VER >= MC_1_18_2
		#if MC_VER < MC_1_19_4
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
		#elif MC_VER < MC_1_21_3
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		#else
		Registry<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
		#endif
			#if MC_VER < MC_1_18_2
			Codec<PalettedContainer<Biome>> biomeCodec = PalettedContainer.codec(
					biomes, biomes.byNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getOrThrow(Biomes.PLAINS));
			#elif MC_VER < MC_1_19_2
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codec(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
			#elif MC_VER < MC_1_21_3
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codecRW(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getHolderOrThrow(Biomes.PLAINS));
			#else
			Codec<PalettedContainer<Holder<Biome>>> biomeCodec = PalettedContainer.codecRW(
				biomes.asHolderIdMap(), biomes.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomes.getOrThrow(Biomes.PLAINS));
			#endif
		#endif
		int sectionYIndex = #if MC_VER < MC_1_17_1 16; #else level.getSectionsCount(); #endif
		LevelChunkSection[] chunkSections = new LevelChunkSection[sectionYIndex];
		
		boolean isLightOn = chunkData.getBoolean("isLightOn");
		boolean hasSkyLight = level.dimensionType().hasSkyLight();
		ListTag tagSections = chunkData.getList("Sections", 10);
		if (tagSections.isEmpty()) tagSections = chunkData.getList("sections", 10);
		
		for (int j = 0; j < tagSections.size(); ++j)
		{
			CompoundTag tagSection = tagSections.getCompound(j);
			int sectionYPos = tagSection.getByte("Y");

			#if MC_VER < MC_1_18_2
			if (tagSection.contains("Palette", 9) && tagSection.contains("BlockStates", 12))
			{
				LevelChunkSection levelChunkSection = new LevelChunkSection(sectionYPos << 4);
				levelChunkSection.getStates().read(tagSection.getList("Palette", 10),
						tagSection.getLongArray("BlockStates"));
				levelChunkSection.recalcBlockCounts();
				if (!levelChunkSection.isEmpty())
					chunkSections[#if MC_VER < MC_1_17_1 sectionYPos #else level.getSectionIndexFromSectionY(sectionYPos) #endif ]
							= levelChunkSection;
			}
			#else
			int sectionId = level.getSectionIndexFromSectionY(sectionYPos);
			if (sectionId >= 0 && sectionId < chunkSections.length)
			{
				PalettedContainer<BlockState> blockStateContainer;
				#if MC_VER < MC_1_18_2
				PalettedContainer<Biome> biomeContainer;
				#else
				PalettedContainer<Holder<Biome>> biomeContainer;
				#endif
				
				blockStateContainer = tagSection.contains("block_states", 10)
						? BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, tagSection.getCompound("block_states"))
							.promotePartial(string -> logBlockDeserializationWarning(chunkPos, sectionYPos, string))
						#if MC_VER < MC_1_20_6 
						.getOrThrow(false, (message) -> logWarningOnce(message))
						#else
						.getOrThrow((message) -> logErrorAndReturnException(message)) 
						#endif
						: new PalettedContainer<BlockState>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);

				#if MC_VER < MC_1_18_2
				biomeContainer = tagSection.contains("biomes", 10)
						? biomeCodec.parse(NbtOps.INSTANCE, tagSection.getCompound("biomes")).promotePartial(string -> logErrors(chunkPos, sectionYPos, string)).getOrThrow(false, (message) -> logWarningOnce(message))
						: new PalettedContainer<Biome>(biomes, biomes.getOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES);
				#else
				
				
				if (tagSection.contains("biomes", 10))
				{
					biomeContainer =
						biomeCodec.parse(NbtOps.INSTANCE, tagSection.getCompound("biomes"))
								.promotePartial(string -> logBiomeDeserializationWarning(chunkPos, sectionYIndex, (String) string))
						#if MC_VER < MC_1_20_6 
						.getOrThrow(false, (message) -> logWarningOnce(message));
						#else
						.getOrThrow((message) -> logErrorAndReturnException(message));
						#endif
				}
				else
				{
					biomeContainer = new PalettedContainer<Holder<Biome>>(biomes.asHolderIdMap(), 
							#if MC_VER < MC_1_21_3
							biomes.getHolderOrThrow(Biomes.PLAINS), 
							#else
							biomes.getOrThrow(Biomes.PLAINS),
							#endif
							PalettedContainer.Strategy.SECTION_BIOMES);
				}
				
				#endif
				
				#if MC_VER < MC_1_20_1
				chunkSections[sectionId] = new LevelChunkSection(sectionYPos, blockStateContainer, biomeContainer);
				#else
				chunkSections[sectionId] = new LevelChunkSection(blockStateContainer, biomeContainer);
				#endif
			}
			#endif
			
		}
		return chunkSections;
	}
	private static 
		#if MC_VER < MC_1_20_6 ChunkStatus.ChunkType
		#elif MC_VER < MC_1_21_1 ChunkType
		#else ChunkType #endif 
	readChunkType(CompoundTag tagLevel)
	{
		ChunkStatus chunkStatus = ChunkStatus.byName(tagLevel.getString("Status"));
		if (chunkStatus != null)
		{
			return chunkStatus.getChunkType();
		}
		
		return 
				#if MC_VER <= MC_1_20_4 ChunkStatus.ChunkType.PROTOCHUNK;
				#else ChunkType.PROTOCHUNK; #endif
	}
	private static void readHeightmaps(LevelChunk chunk, CompoundTag chunkData)
	{
		CompoundTag tagHeightmaps = chunkData.getCompound("Heightmaps");
		for (Heightmap.Types type : ChunkStatus.FULL.heightmapsAfter())
		{
			String heightmap = type.getSerializationKey();
			if (tagHeightmaps.contains(heightmap, 12))
				chunk.setHeightmap(type, tagHeightmaps.getLongArray(heightmap));
		}
		Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
	}
	private static void readPostPocessings(LevelChunk chunk, CompoundTag chunkData)
	{
		ListTag tagPostProcessings = chunkData.getList("PostProcessing", 9);
		for (int i = 0; i < tagPostProcessings.size(); ++i)
		{
			ListTag listTag3 = tagPostProcessings.getList(i);
			for (int j = 0; j < listTag3.size(); ++j)
			{
				#if MC_VER < MC_1_21_3
				chunk.addPackedPostProcess(listTag3.getShort(j), i);
				#else
				chunk.addPackedPostProcess(ShortList.of(listTag3.getShort(j)), i);
				#endif
			}
		}
	}
	#if MC_VER >= MC_1_18_2
	private static BlendingData readBlendingData(CompoundTag chunkData)
	{
		BlendingData blendingData = null;
		if (chunkData.contains("blending_data", 10))
		{
			@SuppressWarnings({"unchecked", "rawtypes"})
			Dynamic<CompoundTag> blendingDataTag = new Dynamic(NbtOps.INSTANCE, chunkData.getCompound("blending_data"));
			
			#if MC_VER < MC_1_21_3
			blendingData = BlendingData.CODEC.parse(blendingDataTag).resultOrPartial((message) -> logWarningOnce(message)).orElse(null);
			#else
			blendingData = BlendingData.unpack(BlendingData.Packed.CODEC.parse(blendingDataTag).resultOrPartial((message) -> logWarningOnce(message)).orElse(null));
			#endif
		}
		return blendingData;
	}
	#endif
	
	
	
	//=====================//
	// read chunk lighting //
	//=====================//
	
	/**
	 * https://minecraft.wiki/w/Chunk_format
	 */
	public static CombinedChunkLightStorage readLight(ChunkAccess chunk, CompoundTag chunkData)
	{
		#if MC_VER <= MC_1_17_1
		// MC 1.16 and 1.17 doesn't have the necessary NBT info
		return null;
		#else
		
		CombinedChunkLightStorage combinedStorage = new CombinedChunkLightStorage(ChunkWrapper.getInclusiveMinBuildHeight(chunk), ChunkWrapper.getExclusiveMaxBuildHeight(chunk));
		ChunkLightStorage blockLightStorage = combinedStorage.blockLightStorage;
		ChunkLightStorage skyLightStorage = combinedStorage.skyLightStorage;
		
		boolean foundSkyLight = false;
		
		
		
		//===================//
		// get NBT tags info //
		//===================//
		
		Tag chunkSectionTags = chunkData.get("sections");
		if (chunkSectionTags == null)
		{
			if (!lightingSectionErrorLogged)
			{
				lightingSectionErrorLogged = true;
				LOGGER.error("No sections found for chunk at pos ["+chunk.getPos()+"] chunk data may be out of date.");
			}
			return null;
		}
		else if (!(chunkSectionTags instanceof ListTag))
		{
			if (!lightingSectionErrorLogged)
			{
				lightingSectionErrorLogged = true;
				LOGGER.error("Chunk section tag list have unexpected type ["+chunkSectionTags.getClass().getName()+"], expected ["+ListTag.class.getName()+"].");
			}
			return null;
		}
		ListTag chunkSectionListTag = (ListTag) chunkSectionTags;
		
		
		
		//===================//
		// get lighting info //
		//===================//
		
		for (int sectionIndex = 0; sectionIndex < chunkSectionListTag.size(); sectionIndex++)
		{
			Tag chunkSectionTag = chunkSectionListTag.get(sectionIndex);
			if (!(chunkSectionTag instanceof CompoundTag))
			{
				if (!lightingSectionErrorLogged)
				{
					lightingSectionErrorLogged = true;
					LOGGER.error("Chunk section tag has an unexpected type ["+chunkSectionTag.getClass().getName()+"], expected ["+CompoundTag.class.getName()+"].");
				}
				return null;
			}
			CompoundTag chunkSectionCompoundTag = (CompoundTag) chunkSectionTag;
			
			
			// if null all lights = 0
			byte[] blockLightNibbleArray = chunkSectionCompoundTag.getByteArray("BlockLight");
			byte[] skyLightNibbleArray = chunkSectionCompoundTag.getByteArray("SkyLight");
			
			// if any sky light was found then all lights above will be max brightness
			if (skyLightNibbleArray.length != 0)
			{
				foundSkyLight = true;
			}
			
			for (int relX = 0; relX < LodUtil.CHUNK_WIDTH; relX++)
			{
				for (int relZ = 0; relZ < LodUtil.CHUNK_WIDTH; relZ++)
				{
					// chunk sections are also 16 blocks tall
					for (int relY = 0; relY < LodUtil.CHUNK_WIDTH; relY++)
					{
						int blockPosIndex = relY*16*16 + relZ*16 + relX;
						byte blockLight = (blockLightNibbleArray.length == 0) ? 0 : getNibbleAtIndex(blockLightNibbleArray, blockPosIndex);
						byte skyLight = (skyLightNibbleArray.length == 0) ? 0 : getNibbleAtIndex(skyLightNibbleArray, blockPosIndex);
						if (skyLightNibbleArray.length == 0 && foundSkyLight)
						{
							skyLight = LodUtil.MAX_MC_LIGHT;
						}
						
						int y = relY + (sectionIndex * LodUtil.CHUNK_WIDTH) + ChunkWrapper.getInclusiveMinBuildHeight(chunk);
						blockLightStorage.set(relX, y, relZ, blockLight);
						skyLightStorage.set(relX, y, relZ, skyLight);
					}
				}
			}
		}
		
		return combinedStorage;
		#endif
	}
	/** source: https://minecraft.wiki/w/Chunk_format#Block_Format */
	private static byte getNibbleAtIndex(byte[] arr, int index)
	{
		if (index % 2 == 0)
		{
			return (byte)(arr[index/2] & 0x0F);
		}
		else
		{
			return (byte)((arr[index/2]>>4) & 0x0F);
		}
	}
	
	
	
	//=========//
	// logging //
	//=========//
	
	private static void logBlockDeserializationWarning(ChunkPos chunkPos, int sectionYIndex, String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Unable to deserialize blocks for chunk section [" + chunkPos.x + ", " + sectionYIndex + ", " + chunkPos.z + "], error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
	}
	private static void logBiomeDeserializationWarning(ChunkPos chunkPos, int sectionYIndex, String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) -> 
		{
			LOGGER.warn("Unable to deserialize biomes for chunk section [" + chunkPos.x + ", " + sectionYIndex + ", " + chunkPos.z + "], error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
	}
	
	private static void logWarningOnce(String message) { logWarningOnce(message, null); }
	private static void logWarningOnce(String message, Exception e)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Parsing error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.",
					e);
			
			return newMessage;
		});
	}
	
	private static RuntimeException logErrorAndReturnException(String message)
	{
		LOGGED_ERROR_MESSAGE_MAP.computeIfAbsent(message, (newMessage) ->
		{
			LOGGER.warn("Parsing error: ["+newMessage+"]. " +
					"This can probably be ignored, although if your world looks wrong, optimizing it via the single player menu then deleting your DH database(s) should fix the problem.");
			
			return newMessage;
		});
		
		// Currently we want to ignore these errors, if returning null is a problem, we can change this later
		return null; //new RuntimeException(message);
	}
	
	
	
	
	//================//
	// helper classes //
	//================//
	
	public static class CombinedChunkLightStorage
	{
		public ChunkLightStorage blockLightStorage;
		public ChunkLightStorage skyLightStorage;
		
		public CombinedChunkLightStorage(int minY, int maxY)
		{
			this.blockLightStorage = ChunkLightStorage.createBlockLightStorage(minY, maxY);
			this.skyLightStorage = ChunkLightStorage.createSkyLightStorage(minY, maxY);
		}
	}
	
}

