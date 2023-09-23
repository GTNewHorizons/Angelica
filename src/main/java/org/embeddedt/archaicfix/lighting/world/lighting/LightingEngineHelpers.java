package org.embeddedt.archaicfix.lighting.world.lighting;

import atomicstryker.dynamiclights.client.DynamicLights;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class LightingEngineHelpers {
    private static final Block DEFAULT_BLOCK_STATE = Blocks.air;

    // Avoids some additional logic in Chunk#getBlockState... 0 is always air
    static Block posToState(final BlockPos pos, final Chunk chunk) {
        return posToState(pos, chunk.getBlockStorageArray()[pos.getY() >> 4]);
    }

    static Block posToState(final BlockPos pos, final ExtendedBlockStorage section) {
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();

        if (section != null)
        {
            return section.getBlockByExtId(x & 15, y & 15, z & 15);
        }

        return DEFAULT_BLOCK_STATE;
    }

    static int getLightValueForState(final Block state, final IBlockAccess world, final int x, final int y, final int z) {
        if(LightingEngine.isDynamicLightsLoaded) {
            return DynamicLights.getLightValue(world, state, x, y, z);
        } else
            return state.getLightValue(world, x, y, z);
    }

    public static Chunk getLoadedChunk(final IChunkProvider provider, int chunkX, int chunkZ) {
        if(!provider.chunkExists(chunkX, chunkZ))
            return null;
        return provider.provideChunk(chunkX, chunkZ);
    }
}
