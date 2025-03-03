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
package com.seibel.distanthorizons.common.wrappers.chunk;

import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.MutableBlockPosWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IMutableBlockPosWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import org.apache.logging.log4j.Logger;

import java.util.*;

#if MC_VER >= MC_1_17_1
import net.minecraft.core.QuartPos;
#endif

#if MC_VER == MC_1_16_5
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_17_1
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_18_2
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER == MC_1_19_2 || MC_VER == MC_1_19_4
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER >= MC_1_20_1
import net.minecraft.world.level.chunk.LevelChunkSection;
#endif

#if MC_VER <= MC_1_20_4
import net.minecraft.world.level.chunk.ChunkStatus;
#else
import net.minecraft.world.level.chunk.status.ChunkStatus;
#endif


public class ChunkWrapper implements IChunkWrapper
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	/** can be used for interactions with the underlying chunk where creating new BlockPos objects could cause issues for the garbage collector. */
	private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_BLOCK_POS_REF = ThreadLocal.withInitial(() -> new BlockPos.MutableBlockPos());
	private static final ThreadLocal<MutableBlockPosWrapper> MUTABLE_BLOCK_POS_WRAPPER_REF = ThreadLocal.withInitial(() -> new MutableBlockPosWrapper());
	
	
	private final ChunkAccess chunk;
	private final DhChunkPos chunkPos;
	private final ILevelWrapper wrappedLevel;
	
	private boolean isDhBlockLightCorrect = false;
	private boolean isDhSkyLightCorrect = false;
	
	private ChunkLightStorage blockLightStorage;
	private ChunkLightStorage skyLightStorage;
	
	private ArrayList<DhBlockPos> blockLightPosList = null;
	
	private int minNonEmptyHeight = Integer.MIN_VALUE;
	private int maxNonEmptyHeight = Integer.MAX_VALUE;
	
	/** will be null if we are using MC heightmaps */
	private final int[][] solidHeightMap;
	/** will be null if we are using MC heightmaps */
	private final int[][] lightBlockingHeightMap;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkWrapper(ChunkAccess chunk, ILevelWrapper wrappedLevel)
	{
		this.chunk = chunk;
		this.wrappedLevel = wrappedLevel;
		this.chunkPos = new DhChunkPos(chunk.getPos().x, chunk.getPos().z);
		
		// use DH heightmaps if requested
		if (Config.Common.LodBuilding.recalculateChunkHeightmaps.get())
		{
			this.solidHeightMap = new int[LodUtil.CHUNK_WIDTH][LodUtil.CHUNK_WIDTH];
			this.lightBlockingHeightMap = new int[LodUtil.CHUNK_WIDTH][LodUtil.CHUNK_WIDTH];
			
			this.recalculateDhHeightMapsIfNeeded();
		}
		else
		{
			this.solidHeightMap = null;
			this.lightBlockingHeightMap = null;
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	public int getHeight() { return getHeight(this.chunk); }
	public static int getHeight(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_17_1
		return 255;
		#else
		return chunk.getHeight();
		#endif
	}
	
	@Override
	public int getInclusiveMinBuildHeight() { return getInclusiveMinBuildHeight(this.chunk); }
	public static int getInclusiveMinBuildHeight(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_17_1
		return 0;
		#elif MC_VER < MC_1_21_3
		return chunk.getMinBuildHeight();
		#else
		return chunk.getMinY();
		#endif
	}
	
	@Override
	public int getExclusiveMaxBuildHeight() { return getExclusiveMaxBuildHeight(this.chunk); }
	public static int getExclusiveMaxBuildHeight(ChunkAccess chunk) 
	{
		#if MC_VER < MC_1_21_3
		return chunk.getMaxBuildHeight();
		#else
		// +1 since Minecraft made the max value inclusive
		return chunk.getMaxY() + 1;
		#endif
	}
	
	@Override
	public int getMinNonEmptyHeight()
	{
		if (this.minNonEmptyHeight != Integer.MIN_VALUE)
		{
			return this.minNonEmptyHeight;
		}
		
		
		// default if every section is empty or missing
		this.minNonEmptyHeight = this.getInclusiveMinBuildHeight();
		
		// determine the lowest empty section (bottom up)
		LevelChunkSection[] sections = this.chunk.getSections();
		for (int index = 0; index < sections.length; index++)
		{
			if (sections[index] == null)
			{
				continue;
			}
			
			if (!isChunkSectionEmpty(sections[index]))
			{
				this.minNonEmptyHeight = this.getChunkSectionMinHeight(index);
				break;
			}
		}
		
		return this.minNonEmptyHeight;
	}
	
	
	@Override
	public int getMaxNonEmptyHeight()
	{
		if (this.maxNonEmptyHeight != Integer.MAX_VALUE)
		{
			return this.maxNonEmptyHeight;
		}
		
		
		// default if every section is empty or missing
		this.maxNonEmptyHeight = this.getExclusiveMaxBuildHeight();
		
		// determine the highest empty section (top down)
		LevelChunkSection[] sections = this.chunk.getSections();
		for (int index = sections.length-1; index >= 0; index--)
		{
			// update at each position to fix using the max height if the chunk is empty
			this.maxNonEmptyHeight = this.getChunkSectionMinHeight(index) + 16;
			
			if (sections[index] == null)
			{
				continue;
			}
			
			if (!isChunkSectionEmpty(sections[index]))
			{
				// non-empty section found
				break;
			}
		}
		
		return this.maxNonEmptyHeight;
	}
	private static boolean isChunkSectionEmpty(LevelChunkSection section)
	{
		#if MC_VER == MC_1_16_5
		return section.isEmpty();
		#elif MC_VER == MC_1_17_1
		return section.isEmpty();
		#else
		return section.hasOnlyAir();
		#endif
	}
	private int getChunkSectionMinHeight(int index) { return (index * 16) + this.getInclusiveMinBuildHeight(); }
	
	/** Will only run if the config says the MC heightmaps shouldn't be trusted. */
	public void recalculateDhHeightMapsIfNeeded()
	{
		// re-calculate the min/max heights for consistency (during world gen these may be wrong)
		this.minNonEmptyHeight = Integer.MIN_VALUE;
		this.maxNonEmptyHeight = Integer.MAX_VALUE;
		
		
		// recalculate heightmaps if needed
		if (this.solidHeightMap != null)
		{
			for (int x = 0; x < LodUtil.CHUNK_WIDTH; x++)
			{
				for (int z = 0; z < LodUtil.CHUNK_WIDTH; z++)
				{
					int minInclusiveBuildHeight = this.getMinNonEmptyHeight();
					// if no blocks are found the height map will be at the bottom of the world
					int solidHeight = minInclusiveBuildHeight;
					int lightBlockingHeight = minInclusiveBuildHeight;
					
					
					int y = this.getMaxNonEmptyHeight(); //this.getExclusiveMaxBuildHeight();
					IBlockStateWrapper block = this.getBlockState(x, y, z);
					while (// go down until we reach the minimum build height
							y > minInclusiveBuildHeight
							// keep going until we find both height map values
							&& (solidHeight == minInclusiveBuildHeight || lightBlockingHeight == minInclusiveBuildHeight))
					{
						// is this block solid?
						if (solidHeight == minInclusiveBuildHeight
							&& block.isSolid())
						{
							solidHeight = y;
						}
						
						// is this block light blocking?
						if (lightBlockingHeight == minInclusiveBuildHeight
							&& block.getOpacity() != LodUtil.BLOCK_FULLY_TRANSPARENT)
						{
							lightBlockingHeight = y;
						}
						
						// get the next block down
						y--;
						block = this.getBlockState(x, y, z);
					}
					
					this.solidHeightMap[x][z] = solidHeight;
					this.lightBlockingHeightMap[x][z] = lightBlockingHeight;
				}
			}
		}
	}
	
	@Override
	public int getSolidHeightMapValue(int xRel, int zRel) 
	{ 
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);
		
		// will be null if we want to use MC heightmaps
		if (this.solidHeightMap == null)
		{
			return this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).getFirstAvailable(xRel, zRel);	
		}
		else
		{
			return this.solidHeightMap[xRel][zRel];
		}
	}
	
	@Override
	public int getLightBlockingHeightMapValue(int xRel, int zRel) 
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);
		
		if (this.lightBlockingHeightMap == null)
		{
			return this.chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).getFirstAvailable(xRel, zRel);
		}
		else
		{
			return this.lightBlockingHeightMap[xRel][zRel];
		} 
	}
	
	
	@Override
	public IBiomeWrapper getBiome(int relX, int relY, int relZ)
	{
		#if MC_VER < MC_1_17_1
		return BiomeWrapper.getBiomeWrapper(this.chunk.getBiomes().getNoiseBiome(
				relX >> 2, relY >> 2, relZ >> 2),
				this.wrappedLevel);
		#elif MC_VER < MC_1_18_2
		return BiomeWrapper.getBiomeWrapper(this.chunk.getBiomes().getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#elif MC_VER < MC_1_18_2
		return BiomeWrapper.getBiomeWrapper(this.chunk.getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#else 
		//Now returns a Holder<Biome> instead of Biome
		return BiomeWrapper.getBiomeWrapper(this.chunk.getNoiseBiome(
				QuartPos.fromBlock(relX), QuartPos.fromBlock(relY), QuartPos.fromBlock(relZ)),
				this.wrappedLevel);
		#endif
	}
	
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		
		BlockPos.MutableBlockPos blockPos = MUTABLE_BLOCK_POS_REF.get();
		
		blockPos.setX(relX);
		blockPos.setY(relY);
		blockPos.setZ(relZ);
		
		// TODO copy into pooled array, this isn't thread safe and can cause MC to throw errors if the chunk is loaded
		return BlockStateWrapper.fromBlockState(this.chunk.getBlockState(blockPos), this.wrappedLevel);
	}
	
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		
		BlockPos.MutableBlockPos pos = (BlockPos.MutableBlockPos)mcBlockPos.getWrappedMcObject();
		pos.setX(relX);
		pos.setY(relY);
		pos.setZ(relZ);
		
		return BlockStateWrapper.fromBlockState(this.chunk.getBlockState(pos), this.wrappedLevel, guess);
	}
	
	/**
	
	 // Commented out experimental LevelChunkSection cloning logic to fix extremely rare concurrency modification issue
	 // James has only ever seen a report relating to LevelSection concurrent modification once,
	 // the issue can cause DH lighting/LOD building to fail due to the chunk being modified on the server.
	 // James has only heard of this issue once, so it isn't a high priority issue.
	 // And from James' quick look at a few different MC versions it appears the LevelChunkSection object changes quite drastically between MC versions,
	 // meaning any cloning logic would have to either be a new wrapper or very MC version dependent, either way a lot of additional work.
	 // Due to the large time cost and extremely rare nature of the issue, this logic is commented out unless this issue pops up again in the future. 
	
	 // instance variable to hold the cloned sections
	private final LevelChunkSection[] levelChunkSections;
	
	 // new constructor logic to clone the sections
	public constructor(...)
	{
		// other constructor logic //
		
		LevelChunkSection[] sections = this.chunk.getSections();	 
		this.levelChunkSections = new LevelChunkSection[sections.length];
		for (int i = 0; i < sections.length; i++)
		{
			LevelChunkSection section = sections[i];
			if (section != null)
			{
	            // TODO implement section cloning for older MC versions, only 1.21.4 MC (and maybe other semi recent versions) have a clean way to handle this
	            // TODO we probably want a wrapper object instead
				#if MC_VER < MC_1_21_4
				this.levelChunkSections[i] = section;
				#else
				this.levelChunkSections[i] = section.copy();
				#endif
			}
		}
	}
	
	 // replacement getters
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		return this.getBlockStateInternal(relX, relY, relZ, null);
	}
	
	@Override
	public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);
		return this.getBlockStateInternal(relX, relY, relZ, guess);
	}
	
	// internal getter logic
	private IBlockStateWrapper getBlockStateInternal(int relX, int y, int relZ, @Nullable IBlockStateWrapper guess)
	{
		try
		{
			// attempt to get the section for this position
			int i = (y - this.getInclusiveMinBuildHeight()) / 16;
			if (i >= 0 && i < this.levelChunkSections.length)
			{
				LevelChunkSection section = this.levelChunkSections[i];
				if (!section.hasOnlyAir())
				{
					if (guess != null)
					{
						return BlockStateWrapper.fromBlockState(section.getBlockState(relX & 15, y & 15, relZ & 15), this.wrappedLevel, guess);
					}
					else
					{
						return BlockStateWrapper.fromBlockState(section.getBlockState(relX & 15, y & 15, relZ & 15), this.wrappedLevel);
					}
				}
			}
			
			return BlockStateWrapper.AIR;
		}
		catch (Exception e)
		{
			return BlockStateWrapper.AIR;
		}
	}
	 */
	
	
	
	@Override
	public IMutableBlockPosWrapper getMutableBlockPosWrapper() { return MUTABLE_BLOCK_POS_WRAPPER_REF.get(); }
	
	@Override
	public DhChunkPos getChunkPos() { return this.chunkPos; }
	
	public ChunkAccess getChunk() { return this.chunk; }
	
	public void trySetStatus(ChunkStatus status) { trySetStatus(this.getChunk(), status); }
	/** does nothing if the chunk object doesn't support setting it's status */
	public static void trySetStatus(ChunkAccess chunk, ChunkStatus status)
	{
		if (chunk instanceof ProtoChunk)
		{
			#if MC_VER < MC_1_21_1
			((ProtoChunk) chunk).setStatus(status);
			#else
			((ProtoChunk) chunk).setPersistedStatus(status);
			#endif
		}
	}
	
	public ChunkStatus getStatus() { return getStatus(this.getChunk()); }
	public static ChunkStatus getStatus(ChunkAccess chunk)
	{
		#if MC_VER < MC_1_21_1 
		return chunk.getStatus();
		#else
		return chunk.getPersistedStatus(); 
		#endif
	}
	
	@Override
	public int getMaxBlockX() { return this.chunk.getPos().getMaxBlockX(); }
	@Override
	public int getMaxBlockZ() { return this.chunk.getPos().getMaxBlockZ(); }
	@Override
	public int getMinBlockX() { return this.chunk.getPos().getMinBlockX(); }
	@Override
	public int getMinBlockZ() { return this.chunk.getPos().getMinBlockZ(); }
	
	
	
	//==========//
	// lighting //
	//==========//
	
	@Override 
	public void setIsDhSkyLightCorrect(boolean isDhLightCorrect) { this.isDhSkyLightCorrect = isDhLightCorrect; }
	@Override 
	public void setIsDhBlockLightCorrect(boolean isDhLightCorrect) { this.isDhBlockLightCorrect = isDhLightCorrect; }
	
	@Override
	public boolean isDhBlockLightingCorrect() { return this.isDhBlockLightCorrect; }
	@Override
	public boolean isDhSkyLightCorrect() { return this.isDhSkyLightCorrect; }
	
	
	@Override
	public int getDhBlockLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getBlockLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhBlockLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getBlockLightStorage().set(relX, y, relZ, lightValue);
	}
	
	private ChunkLightStorage getBlockLightStorage()
	{
		if (this.blockLightStorage == null)
		{
			this.blockLightStorage = ChunkLightStorage.createBlockLightStorage(this);
		}
		return this.blockLightStorage;
	}
	public void setBlockLightStorage(ChunkLightStorage lightStorage) { this.blockLightStorage = lightStorage; }
	@Override
	public void clearDhBlockLighting() { this.getBlockLightStorage().clear(); }
	
	
	@Override
	public int getDhSkyLight(int relX, int y, int relZ)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		return this.getSkyLightStorage().get(relX, y, relZ);
	}
	@Override
	public void setDhSkyLight(int relX, int y, int relZ, int lightValue)
	{
		this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, y, relZ);
		this.getSkyLightStorage().set(relX, y, relZ, lightValue);
	}
	@Override
	public void clearDhSkyLighting() { this.getSkyLightStorage().clear(); }
	
	private ChunkLightStorage getSkyLightStorage()
	{
		if (this.skyLightStorage == null)
		{
			this.skyLightStorage = ChunkLightStorage.createSkyLightStorage(this);
		}
		return this.skyLightStorage;
	}
	public void setSkyLightStorage(ChunkLightStorage lightStorage) { this.skyLightStorage = lightStorage; }
	
	
	/** 
	 * FIXME synchronized is necessary for a rare issue where this method is called from two separate threads at the same time
	 *  before the list has finished populating.
	 */
	@Override
	public synchronized ArrayList<DhBlockPos> getWorldBlockLightPosList()
	{
		// only populate the list once
		if (this.blockLightPosList == null)
		{
			this.blockLightPosList = new ArrayList<>();
			
			
			#if MC_VER < MC_1_20_1
			this.chunk.getLights().forEach((blockPos) ->
			{
				this.blockLightPosList.add(new DhBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
			});
			#else
			this.chunk.findBlockLightSources((blockPos, blockState) ->
			{
				DhBlockPos pos = new DhBlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
				
				// this can be uncommented if MC decides to return relative block positions in the future instead of world positions
				//pos.mutateToChunkRelativePos(pos);
				//pos.mutateOffset(this.chunkPos.getMinBlockX(), 0, this.chunkPos.getMinBlockZ(), pos);
				
				this.blockLightPosList.add(pos);
			});
			#endif
		}
		
		return this.blockLightPosList;
	}
	
	
	
	//===============//
	// other methods //
	//===============//
	
	@Override
	public boolean isStillValid() { return this.wrappedLevel.tryGetChunk(this.chunkPos) == this; }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString() { return this.chunk.getClass().getSimpleName() + this.chunk.getPos(); }
	
	//@Override 
	//public int hashCode()
	//{
	//	if (this.blockBiomeHashCode == 0)
	//	{
	//		this.blockBiomeHashCode = this.getBlockBiomeHashCode();
	//	}
	//	
	//	return this.blockBiomeHashCode;
	//}
	
}
