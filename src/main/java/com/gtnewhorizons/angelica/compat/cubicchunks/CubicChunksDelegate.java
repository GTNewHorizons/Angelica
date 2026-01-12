package com.gtnewhorizons.angelica.compat.cubicchunks;

import net.minecraft.client.Minecraft;

import com.cardinalstar.cubicchunks.modcompat.angelica.AngelicaInterop;
import com.cardinalstar.cubicchunks.modcompat.angelica.IAngelicaDelegate;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import me.jellysquid.mods.sodium.client.render.chunk.map.IChunkTracker;

public class CubicChunksDelegate implements IAngelicaDelegate {

    public static final CubicChunksDelegate INSTANCE = new CubicChunksDelegate();

    public static void init() {
        AngelicaInterop.setDelegate(INSTANCE);
    }

    private static IChunkTracker getTracker() {
        return ChunkTrackerHolder.get(Minecraft.getMinecraft().theWorld);
    }

    @Override
    public void onColumnLoaded(int chunkX, int chunkZ) {
        getTracker().onChunkAdded(chunkX, chunkZ);
    }

    @Override
    public void onColumnUnloaded(int chunkX, int chunkZ) {
        getTracker().onChunkRemoved(chunkX, chunkZ);
    }

    @Override
    public void onCubeLoaded(int cubeX, int cubeY, int cubeZ) {
        getTracker().onCubeAdded(cubeX, cubeY, cubeZ);
    }

    @Override
    public void onCubeUnloaded(int cubeX, int cubeY, int cubeZ) {
        getTracker().onCubeRemoved(cubeX, cubeY, cubeZ);
    }
}
