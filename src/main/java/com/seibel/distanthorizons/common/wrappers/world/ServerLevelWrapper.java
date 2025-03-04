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

package com.seibel.distanthorizons.common.wrappers.world;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.common.wrappers.McObjectConverter;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.FakeBlockState;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;

import net.minecraft.block.Block;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Logger;

public class ServerLevelWrapper implements IServerLevelWrapper
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger();
    private static final ConcurrentHashMap<WorldServer, ServerLevelWrapper> LEVEL_WRAPPER_BY_SERVER_LEVEL = new ConcurrentHashMap<>();

    private final WorldServer level;
    @Deprecated // TODO circular references are bad
    private IDhLevel parentDhLevel;



    //==============//
    // constructors //
    //==============//

    public static ServerLevelWrapper getWrapper(WorldServer level)
    { return LEVEL_WRAPPER_BY_SERVER_LEVEL.computeIfAbsent(level, ServerLevelWrapper::new); }

    public ServerLevelWrapper(WorldServer level) { this.level = level; }



    //=========//
    // methods //
    //=========//

    @Override
    public File getMcSaveFolder()
    {
        return this.level.getChunkSaveLocation(); // TODO ?
    }

    @Override
    public String getWorldFolderName()
    {
		return this.level.provider.getSaveFolder();
    }

    @Override
    public DimensionTypeWrapper getDimensionType() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId); }

    @Override
    public String getDimensionName() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId).getName(); }

    @Override
    public long getHashedSeed() { return this.level.getSeed(); } // TODO?

    @Override
    public String getDhIdentifier() { return this.getDimensionName(); }

    @Override
    public EDhApiLevelType getLevelType() { return EDhApiLevelType.SERVER_LEVEL; }

    public WorldServer getLevel() { return this.level; }

    @Override
    public boolean hasCeiling() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId).hasCeiling(); }

    @Override
    public boolean hasSkyLight() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId).hasSkyLight(); }

    @Override
    public int getMaxHeight() { return this.level.getHeight(); }

    @Override
    public int getMinHeight()
    {
        return 0;
    }

    @Override
    public IChunkWrapper tryGetChunk(DhChunkPos pos)
    {
        if (!this.level.theChunkProviderServer.chunkExists(pos.getX(), pos.getZ()))
        {
            return null;
        }

        Chunk chunk = this.level.theChunkProviderServer.provideChunk(pos.getX(), pos.getZ());
        if (chunk == null)
        {
            return null;
        }

        return new ChunkWrapper(chunk, this);
    }

    @Override
    public boolean hasChunkLoaded(int chunkX, int chunkZ)
    {
        // world.hasChunk(chunkX, chunkZ); THIS DOES NOT WORK FOR CLIENT LEVEL CAUSE MOJANG ALWAYS RETURN TRUE FOR THAT!
        return this.level.theChunkProviderServer.chunkExists(chunkX, chunkZ); // TODO?
    }

    @Override
    public IBlockStateWrapper getBlockState(DhBlockPos pos)
    {
        Block block = this.level.getBlock(pos.getX(), pos.getY(), pos.getZ());
        int meta = this.level.getBlockMetadata(pos.getX(), pos.getY(), pos.getZ());
        return BlockStateWrapper.fromBlockState(new FakeBlockState(block, meta), this);
    }

    @Override
    public IBiomeWrapper getBiome(DhBlockPos pos)
    {
        return BiomeWrapper.getBiomeWrapper(this.level.getBiomeGenForCoords(pos.getX(), pos.getZ()), this);
    }

    @Override
    public WorldServer getWrappedMcObject() { return this.level; }

    @Override
    public void onUnload() { LEVEL_WRAPPER_BY_SERVER_LEVEL.remove(this.level); }


    @Override
    public void setParentLevel(IDhLevel parentLevel) { this.parentDhLevel = parentLevel; }

    @Override
    public IDhApiCustomRenderRegister getRenderRegister()
    {
        if (this.parentDhLevel == null)
        {
            return null;
        }

        return this.parentDhLevel.getGenericRenderer();
    }

    @Override
    public File getDhSaveFolder()
    {
        if (this.parentDhLevel == null)
        {
            return null;
        }

        return this.parentDhLevel.getSaveStructure().getSaveFolder(this);
    }




    //================//
    // base overrides //
    //================//

    @Override
    public String toString() { return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}"; }

}
