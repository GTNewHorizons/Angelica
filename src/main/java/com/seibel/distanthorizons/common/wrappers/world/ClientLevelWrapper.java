package com.seibel.distanthorizons.common.wrappers.world;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiLevelType;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;
import com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import com.seibel.distanthorizons.common.wrappers.block.FakeBlockState;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.*;
import com.seibel.distanthorizons.core.level.IServerKeyedClientLevel;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.block.IBlockStateWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IBiomeWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class ClientLevelWrapper implements IClientLevelWrapper
{
    private static final Logger LOGGER = DhLoggerBuilder.getLogger(ClientLevelWrapper.class.getSimpleName());
    private static final ConcurrentHashMap<WorldClient, ClientLevelWrapper> LEVEL_WRAPPER_BY_CLIENT_LEVEL = new ConcurrentHashMap<>(); // TODO can leak
    private static final IKeyedClientLevelManager KEYED_CLIENT_LEVEL_MANAGER = SingletonInjector.INSTANCE.get(IKeyedClientLevelManager.class);

    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    private final WorldClient level;
    private final ConcurrentHashMap<FakeBlockState, ClientBlockStateColorCache> blockCache = new ConcurrentHashMap<>();

    private BlockStateWrapper dirtBlockWrapper;
    private BiomeWrapper plainsBiomeWrapper;
    @Deprecated // TODO circular references are bad
    private IDhLevel parentDhLevel;



    //=============//
    // constructor //
    //=============//

    protected ClientLevelWrapper(WorldClient level) { this.level = level; }



    //===============//
    // wrapper logic //
    //===============//

    public static IClientLevelWrapper getWrapper(@NotNull WorldClient level) { return getWrapper(level, false); }

    @Nullable
    public static IClientLevelWrapper getWrapper(@Nullable WorldClient level, boolean bypassLevelKeyManager)
    {
        if (!bypassLevelKeyManager)
        {
            if (level == null)
            {
                return null;
            }

            // used if the client is connected to a server that defines the currently loaded level
            IServerKeyedClientLevel overrideLevel = KEYED_CLIENT_LEVEL_MANAGER.getServerKeyedLevel();
            if (overrideLevel != null)
            {
                return overrideLevel;
            }
        }

        return LEVEL_WRAPPER_BY_CLIENT_LEVEL.computeIfAbsent(level, ClientLevelWrapper::new);
    }

    @Nullable
    @Override
    public IServerLevelWrapper tryGetServerSideWrapper()
    {
        try
        {
            WorldServer[] serverLevels = MINECRAFT.getIntegratedServer().worldServers;

            // attempt to find the server level with the same dimension type
            // TODO this assumes only one level per dimension type, the SubDimensionLevelMatcher will need to be added for supporting multiple levels per dimension
            ServerLevelWrapper foundLevelWrapper = null;

            // TODO: Surely there is a more efficient way to write this code
            for (WorldServer serverLevel : serverLevels)
            {
                if (serverLevel.provider.dimensionId == this.level.provider.dimensionId)
                {
                    foundLevelWrapper = ServerLevelWrapper.getWrapper(serverLevel);
                    break;
                }
            }

            return foundLevelWrapper;
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to get server side wrapper for client level: " + this.level);
            return null;
        }
    }



    //====================//
    // base level methods //
    //====================//

    @Override
    public int getBlockColor(DhBlockPos pos, IBiomeWrapper biome, IBlockStateWrapper blockWrapper)
    {
        ClientBlockStateColorCache blockColorCache = this.blockCache.computeIfAbsent(
            ((BlockStateWrapper) blockWrapper).blockState,
            (block) -> new ClientBlockStateColorCache(block, this));

        return blockColorCache.getColor((BiomeWrapper) biome, pos, this);
    }

    @Override
    public int getDirtBlockColor()
    {
        if (this.dirtBlockWrapper == null)
        {
            try
            {
                this.dirtBlockWrapper = (BlockStateWrapper) BlockStateWrapper.deserialize(BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING, this);
            }
            catch (IOException e)
            {
                // shouldn't happen, but just in case
                LOGGER.warn("Unable to get dirt color with resource location ["+BlockStateWrapper.DIRT_RESOURCE_LOCATION_STRING+"] with level ["+this+"].", e);
                return -1;
            }
        }

        return this.getBlockColor(DhBlockPos.ZERO,BiomeWrapper.EMPTY_WRAPPER, this.dirtBlockWrapper);
    }

    @Override
    public void clearBlockColorCache() { this.blockCache.clear(); }

    @Override
    public IBiomeWrapper getPlainsBiomeWrapper()
    {
        if (this.plainsBiomeWrapper == null)
        {
            try
            {
                this.plainsBiomeWrapper = (BiomeWrapper) BiomeWrapper.deserialize(BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING, this);
            }
            catch (IOException e)
            {
                // shouldn't happen, but just in case
                LOGGER.warn("Unable to get planes biome with resource location ["+BiomeWrapper.PLAINS_RESOURCE_LOCATION_STRING+"] with level ["+this+"].", e);
                return null;
            }
        }

        return this.plainsBiomeWrapper;
    }

    @Override
    public DimensionTypeWrapper getDimensionType() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId); }

    @Override
    public String getDimensionName() { return DimensionTypeWrapper.getDimensionTypeWrapper(this.level.provider.dimensionId).getName(); }

    @Override
    public long getHashedSeed() { return this.level.getSeed(); } // TODO?

    @Override
    public String getDhIdentifier() { return this.getHashedSeedEncoded() + "@" + this.getDimensionName(); }

    @Override
    public EDhApiLevelType getLevelType() { return EDhApiLevelType.CLIENT_LEVEL; }

    public WorldClient getLevel() { return this.level; }

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
        if (!this.level.getChunkProvider().chunkExists(pos.getX(), pos.getZ()))
        {
            return null;
        }

        Chunk chunk = this.level.getChunkProvider().provideChunk(pos.getX(), pos.getZ());
        if (chunk == null)
        {
            return null;
        }

        return new ChunkWrapper(chunk, this);
    }

    @Override
    public boolean hasChunkLoaded(int chunkX, int chunkZ)
    {
        return this.level.getChunkProvider().chunkExists(chunkX, chunkZ); // TODO?
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
    public WorldClient getWrappedMcObject() { return this.level; }

    @Override
    public void onUnload()
    {
        LEVEL_WRAPPER_BY_CLIENT_LEVEL.remove(this.level);
        this.parentDhLevel = null;
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




    //===================//
    // generic rendering //
    //===================//

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
    public Color getCloudColor(float tickDelta)
    {
        Vec3 colorVec3 = this.level.getCloudColour(tickDelta);
        return  new Color((float)colorVec3.xCoord, (float)colorVec3.yCoord, (float)colorVec3.zCoord);
    }



    //================//
    // base overrides //
    //================//

    @Override
    public String toString()
    {
        if (this.level == null)
        {
            return "Wrapped{null}";
        }

        return "Wrapped{" + this.level.toString() + "@" + this.getDhIdentifier() + "}";
    }

}
