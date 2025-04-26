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

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.FakeBlockState;
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

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class ChunkWrapper implements IChunkWrapper
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();

    /** can be used for interactions with the underlying chunk where creating new BlockPos objects could cause issues for the garbage collector. */
    private static final ThreadLocal<BlockPos> MUTABLE_BLOCK_POS_REF = ThreadLocal.withInitial(() -> new BlockPos());
    private static final ThreadLocal<MutableBlockPosWrapper> MUTABLE_BLOCK_POS_WRAPPER_REF = ThreadLocal.withInitial(() -> new MutableBlockPosWrapper());


    private final Chunk chunk;
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

    public ChunkWrapper(Chunk chunk, ILevelWrapper wrappedLevel)
    {
        this.chunk = chunk;
        this.wrappedLevel = wrappedLevel;
        this.chunkPos = new DhChunkPos(chunk.xPosition, chunk.zPosition);

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
    public static int getHeight(Chunk chunk)
    {
        return 255;
    }

    @Override
    public int getInclusiveMinBuildHeight() { return getInclusiveMinBuildHeight(this.chunk); }
    public static int getInclusiveMinBuildHeight(Chunk chunk)
    {
        return 0;
    }

    @Override
    public int getExclusiveMaxBuildHeight() { return getExclusiveMaxBuildHeight(this.chunk); }
    public static int getExclusiveMaxBuildHeight(Chunk chunk)
    {
		return 256;
    }

    @Override
    public int getMinNonEmptyHeight()
    {
        if (this.minNonEmptyHeight != Integer.MIN_VALUE)
        {
            return this.minNonEmptyHeight;
        }

        return 0;
        //return this.chunk.heightMapMinimum; // TODO?
    }


    @Override
    public int getMaxNonEmptyHeight()
    {
        if (this.maxNonEmptyHeight != Integer.MAX_VALUE)
        {
            return this.maxNonEmptyHeight;
        }


       return 255; // TODO
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
        return 255;
        /*this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);

        // will be null if we want to use MC heightmaps
        if (this.solidHeightMap == null)
        {
            return this.chunk.getHeightValue(xRel, zRel); // TODO?
        }
        else
        {
            return this.solidHeightMap[xRel][zRel];
        }*/
    }

    @Override
    public int getLightBlockingHeightMapValue(int xRel, int zRel)
    {
        this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(xRel, zRel);

        if (this.lightBlockingHeightMap == null)
        {
            return this.chunk.getHeightValue(xRel, zRel); // TODO
        }
        else
        {
            return this.lightBlockingHeightMap[xRel][zRel];
        }
    }


    @Override
    public IBiomeWrapper getBiome(int relX, int relY, int relZ)
    {
        return BiomeWrapper.getBiomeWrapper(this.chunk.getBiomeGenForWorldCoords(relX, relZ, this.chunk.worldObj.getWorldChunkManager()),
            this.wrappedLevel);
    }

    @Override
    public IBlockStateWrapper getBlockState(int relX, int relY, int relZ)
    {
        this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);

        // TODO copy into pooled array, this isn't thread safe and can cause MC to throw errors if the chunk is loaded
        final Block block = this.chunk.getBlock(relX, relY, relZ);
        final int meta = this.chunk.getBlockMetadata(relX, relY, relZ);
        return BlockStateWrapper.fromBlockAndMeta(block, meta, this.wrappedLevel);
    }

    @Override
    public IBlockStateWrapper getBlockState(int relX, int relY, int relZ, IMutableBlockPosWrapper mcBlockPos, IBlockStateWrapper guess)
    {
        this.throwIndexOutOfBoundsIfRelativePosOutsideChunkBounds(relX, relY, relZ);

        final Block block = this.chunk.getBlock(relX, relY, relZ);
        final int meta = this.chunk.getBlockMetadata(relX, relY, relZ);
        return BlockStateWrapper.fromBlockAndMeta(block, meta, this.wrappedLevel, guess);
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

    public Chunk getChunk() { return this.chunk; }

    @Override
    public int getMaxBlockX() { return this.chunk.xPosition * 16 + 16; }
    @Override
    public int getMaxBlockZ() { return this.chunk.zPosition * 16 + 16; }
    @Override
    public int getMinBlockX() { return this.chunk.xPosition * 16; }
    @Override
    public int getMinBlockZ() { return this.chunk.zPosition * 16; }



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

            for (int x = 0; x  < 16; x++)
            {
                for (int z = 0; z < 16; z++)
                {
                    for (int y = 0; y < 256; y++)
                    {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.getLightValue() > 0)
                        {
                            this.blockLightPosList.add(new DhBlockPos(x + chunkPos.getMinBlockX(), y, z + chunkPos.getMinBlockZ()));
                        }
                    }
                }
            }
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
    public String toString() { return this.chunk.getClass().getSimpleName() + this.chunk.xPosition + "," + this.chunk.zPosition; }

    public boolean isChunkReady() {
        boolean ret = chunk.isTerrainPopulated && chunk.isLightPopulated;
        return ret;
    }

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
