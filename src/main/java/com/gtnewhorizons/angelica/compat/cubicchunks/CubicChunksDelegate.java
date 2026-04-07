package com.gtnewhorizons.angelica.compat.cubicchunks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

import com.cardinalstar.cubicchunks.modcompat.angelica.AngelicaInterop;
import com.cardinalstar.cubicchunks.modcompat.angelica.IAngelicaDelegate;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;

public class CubicChunksDelegate implements IAngelicaDelegate {

    public static final CubicChunksDelegate INSTANCE = new CubicChunksDelegate();

    public static void init() {
        AngelicaInterop.setDelegate(INSTANCE);
    }

    private static WorldClient getWorld() {
        return Minecraft.getMinecraft().theWorld;
    }

    private static RenderGlobal getRenderGlobal() {
        return Minecraft.getMinecraft().renderGlobal;
    }

    private static void scheduleTerrainUpdate() {
        RenderGlobal renderGlobal = getRenderGlobal();
        if (renderGlobal instanceof IRenderGlobalExt ext) {
            ext.angelica$scheduleTerrainUpdate();
        }
    }

    @Override
    public void onColumnLoaded(int chunkX, int chunkZ) {
        WorldClient world = getWorld();
        if (world != null) {
            ChunkTrackerHolder.get(world).onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_ALL);

            CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstance();
            if (renderer.isActive()) {
                for (int cubeY : CubicChunksAPI.getLoadedCubeLevelsInColumn(world, chunkX, chunkZ)) {
                    renderer.getRenderSectionManager().onSectionAdded(chunkX, cubeY, chunkZ);
                    renderer.scheduleRebuildForChunk(chunkX, cubeY, chunkZ, true);
                }
            }
        }
        scheduleTerrainUpdate();
    }

    @Override
    public void onColumnUnloaded(int chunkX, int chunkZ) {
        WorldClient world = getWorld();
        if (world != null) {
            ChunkTrackerHolder.get(world).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
        }
        scheduleTerrainUpdate();
    }

    @Override
    public void onCubeLoaded(int cubeX, int cubeY, int cubeZ) {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstance();
        if (renderer.isActive()) {
            renderer.getRenderSectionManager().onSectionAdded(cubeX, cubeY, cubeZ);
            renderer.scheduleRebuildForChunk(cubeX, cubeY, cubeZ, true);
        }
        scheduleTerrainUpdate();
    }

    @Override
    public void onCubeUnloaded(int cubeX, int cubeY, int cubeZ) {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstance();
        if (renderer.isActive()) {
            renderer.getRenderSectionManager().onSectionRemoved(cubeX, cubeY, cubeZ);
        }
        scheduleTerrainUpdate();
    }
}
