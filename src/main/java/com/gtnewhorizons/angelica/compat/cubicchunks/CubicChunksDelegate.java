package com.gtnewhorizons.angelica.compat.cubicchunks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

import com.cardinalstar.cubicchunks.modcompat.angelica.AngelicaInterop;
import com.cardinalstar.cubicchunks.modcompat.angelica.IAngelicaDelegate;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;

public class CubicChunksDelegate implements IAngelicaDelegate {
    private static final Logger LOGGER = LogManager.getLogger("Angelica|CubicChunks");

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

    private static CeleritasWorldRenderer getRendererIfReady() {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.peekInstance();
        if (renderer == null || !renderer.isActive() || renderer.getRenderSectionManager() == null) {
            return null;
        }
        return renderer;
    }

    private static void scheduleTerrainUpdate() {
        RenderGlobal renderGlobal = getRenderGlobal();
        if (renderGlobal instanceof IRenderGlobalExt ext) {
            ext.angelica$scheduleTerrainUpdate();
        }
    }

    private static void enqueueChunkGraphUpdate(Runnable runnable) {
        AngelicaRenderQueue.submit(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                LOGGER.error("Deferred Cubic Chunks render update failed", t);
            }
        });
    }

    @Override
    public void onColumnLoaded(int chunkX, int chunkZ) {
        try {
            WorldClient world = getWorld();
            if (world != null) {
                ChunkTrackerHolder.get(world).onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_ALL);

                CeleritasWorldRenderer renderer = getRendererIfReady();
                if (renderer != null) {
                    for (int cubeY : CubicChunksAPI.getLoadedCubeLevelsInColumn(world, chunkX, chunkZ)) {
                        final int loadedCubeY = cubeY;
                        enqueueChunkGraphUpdate(() -> {
                            CeleritasWorldRenderer queuedRenderer = getRendererIfReady();
                            if (queuedRenderer != null) {
                                queuedRenderer.scheduleRebuildForChunk(chunkX, loadedCubeY, chunkZ, true);
                            }
                        });
                    }
                }
            }
            scheduleTerrainUpdate();
        } catch (Throwable t) {
            LOGGER.error("Failed handling Cubic Chunks column load at {}, {}", chunkX, chunkZ, t);
        }
    }

    @Override
    public void onColumnUnloaded(int chunkX, int chunkZ) {
        try {
            WorldClient world = getWorld();
            if (world != null) {
                ChunkTrackerHolder.get(world).onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_ALL);
            }
            scheduleTerrainUpdate();
        } catch (Throwable t) {
            LOGGER.error("Failed handling Cubic Chunks column unload at {}, {}", chunkX, chunkZ, t);
        }
    }

    @Override
    public void onCubeLoaded(int cubeX, int cubeY, int cubeZ) {
        try {
            enqueueChunkGraphUpdate(() -> {
                CeleritasWorldRenderer renderer = getRendererIfReady();
                if (renderer != null) {
                    renderer.scheduleRebuildForChunk(cubeX, cubeY, cubeZ, true);
                }
            });
            scheduleTerrainUpdate();
        } catch (Throwable t) {
            LOGGER.error("Failed handling Cubic Chunks cube load at {}, {}, {}", cubeX, cubeY, cubeZ, t);
        }
    }

    @Override
    public void onCubeUnloaded(int cubeX, int cubeY, int cubeZ) {
        try {
            scheduleTerrainUpdate();
        } catch (Throwable t) {
            LOGGER.error("Failed handling Cubic Chunks cube unload at {}, {}, {}", cubeX, cubeY, cubeZ, t);
        }
    }
}
