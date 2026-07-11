package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;

public final class TracyFramePlots {
    private static long lastChunkUpdates;
    private static long lastDrawCalls, lastTexBindMisses, lastProgramSwitches, lastListPlaybacks;
    private static long lastStreamedBytes, lastStreamDraws, lastBufferWraps;
    private static long lastTesrRebuilds, lastTesrRetainedDraws, lastTesrInstDraws, lastTesrInstInstances;
    private static long lastTesrStreamedInstances, lastTesrPromotions, lastTesrCacheHits, lastTesrCacheMisses;
    private static long lastBatcherParts, lastBatcherLiveFallbacks;
    private static final long[] lastBails = new long[ModelPartBatcher.BailReason.VALUES.length];
    private static long lastSectionsUploaded, lastBytesUploaded;

    private TracyFramePlots() {}

    public static void onFrame(Minecraft mc) {
        if (!Tracy.ENABLED) return;

        Tracy.plotAllocRate("allocRate");
        Tracy.plotGcStats();
        final Runtime runtime = Runtime.getRuntime();
        Tracy.plotInt("heapUsed", runtime.totalMemory() - runtime.freeMemory(), TracyBackend.PLOT_FORMAT_MEMORY);
        final int chunksUpdated = WorldRenderer.chunksUpdated;
        Tracy.plotInt("chunkUpdates", chunksUpdated - lastChunkUpdates);
        lastChunkUpdates = chunksUpdated;
        if (mc.renderGlobal != null) {
            Tracy.plotInt("entitiesRendered", mc.renderGlobal.countEntitiesRendered);
        }
        if (mc.theWorld != null) {
            Tracy.plotInt("entitiesTotal", mc.theWorld.loadedEntityList.size());
        }

        lastDrawCalls = delta("gl.drawCalls", GLStateManager.drawCalls, lastDrawCalls);
        lastTexBindMisses = delta("gl.texBindMisses", GLStateManager.texBindMisses, lastTexBindMisses);
        lastProgramSwitches = delta("gl.programSwitches", GLStateManager.programSwitches, lastProgramSwitches);
        lastListPlaybacks = delta("gl.listPlaybacks", DisplayListManager.listPlaybacks, lastListPlaybacks);
        lastStreamedBytes = deltaMem("gl.streamedBytes", TessellatorStreamingDrawer.streamedBytes, lastStreamedBytes);
        lastStreamDraws = delta("gl.streamDraws", TessellatorStreamingDrawer.streamDraws, lastStreamDraws);
        lastBufferWraps = delta("gl.bufferWraps", TessellatorStreamingDrawer.bufferWraps, lastBufferWraps);

        final ShaderManager ffp = ShaderManager.getInstance();
        Tracy.plotInt("ffp.preDrawCalls", ffp.statLastFramePreDrawCalls());
        Tracy.plotInt("ffp.blockWrites", ffp.statLastFrameBlockWrites());
        Tracy.plotInt("ffp.stagedMatrices", ffp.statLastFrameStagedMatrices());
        Tracy.plotInt("ffp.stagedLighting", ffp.statLastFrameStagedLighting());
        Tracy.plotInt("ffp.stagedFragment", ffp.statLastFrameStagedFragment());
        Tracy.plotInt("ffp.stagedColor", ffp.statLastFrameStagedColor());
        Tracy.plotInt("ffp.stagedNormal", ffp.statLastFrameStagedNormal());
        Tracy.plotInt("ffp.stagedTexCoord", ffp.statLastFrameStagedTexCoord());
        Tracy.plotInt("ffp.stagedLightmap", ffp.statLastFrameStagedLightmap());
        Tracy.plotInt("ffp.stagedTexGen", ffp.statLastFrameStagedTexGen());
        Tracy.plotInt("ffp.stagedClipPlanes", ffp.statLastFrameStagedClipPlanes());
        Tracy.plotInt("ffp.stagedMisc", ffp.statLastFrameStagedMisc());
        Tracy.plotInt("ffp.programs", ffp.statProgramCount());

        final TesrBatchRenderer tesr = TesrBatchRenderer.INSTANCE;
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;

        lastTesrRebuilds = delta("tesr.rebuilds", tesr.statRebuilds(), lastTesrRebuilds);
        lastTesrRetainedDraws = delta("tesr.retainedDraws", tesr.statRetainedDraws(), lastTesrRetainedDraws);
        lastTesrInstDraws = delta("tesr.instancedDraws", tesr.statInstancedDraws(), lastTesrInstDraws);
        lastTesrInstInstances = delta("tesr.instancedInstances", tesr.statInstancedInstances(), lastTesrInstInstances);
        lastTesrStreamedInstances = delta("tesr.streamedInstances", tesr.statStreamedInstances(), lastTesrStreamedInstances);
        lastTesrPromotions = delta("tesr.volatilePromotions", tesr.statVolatilePromotions(), lastTesrPromotions);
        Tracy.plotInt("tesr.retainedBytes", tesr.statRetainedBytes(), TracyBackend.PLOT_FORMAT_MEMORY);
        Tracy.plotInt("tesr.bufferSourceBytes", tesr.statBufferSourceBytes(), TracyBackend.PLOT_FORMAT_MEMORY);
        lastTesrCacheHits = delta("tesr.cacheHits", AngelicaTesrMeshCache.cacheHits, lastTesrCacheHits);
        lastTesrCacheMisses = delta("tesr.cacheMisses", AngelicaTesrMeshCache.cacheMisses, lastTesrCacheMisses);
        lastBatcherParts = delta("tesr.modelParts", batcher.statParts(), lastBatcherParts);
        lastBatcherLiveFallbacks = delta("tesr.liveFallbacks", batcher.statLiveFallbacks(), lastBatcherLiveFallbacks);
        for (final ModelPartBatcher.BailReason reason : ModelPartBatcher.BailReason.VALUES) {
            lastBails[reason.ordinal()] = delta(reason.plotName, batcher.statBail(reason), lastBails[reason.ordinal()]);
        }

        lastSectionsUploaded = delta("mesh.uploadedSections", RenderRegionManager.getSectionsUploaded(), lastSectionsUploaded);
        lastBytesUploaded = deltaMem("mesh.uploadedBytes", RenderRegionManager.getBytesUploaded(), lastBytesUploaded);
        final CeleritasWorldRenderer cwr = CeleritasWorldRenderer.getInstanceOrNull();
        if (cwr != null && cwr.isActive()) {
            cwr.getRenderSectionManager().tracyPlots();
        }

        Tracy.plotInt("queue.depth", AngelicaRenderQueue.getQueueDepth());
        Tracy.plotInt("queue.tasksRan", AngelicaRenderQueue.getLastFrameTasksRan());
        Tracy.plotInt("queue.timeNs", AngelicaRenderQueue.getLastFrameTimeNs());

        if (DynamicLights.isEnabled()) {
            final DynamicLights dl = DynamicLights.get();
            Tracy.plotInt("dyn.lightSources", dl.getLightSourcesCount());
            Tracy.plotInt("dyn.updates", dl.getLastUpdateCount());
            if (DynamicLights.FrustumCullingEnabled) {
                Tracy.plotInt("dyn.pendingRebuilds", dl.getChunkRebuildManager().getPendingCount());
            }
        }

        RenderClassTimings.ENTITY.flushFrame();
        RenderClassTimings.SHADOW_ENTITY.flushFrame();
        RenderClassTimings.TESR.flushFrame();
        BailClassCounts.MATERIAL.flushFrame();
        BailClassCounts.TEMPLATE.flushFrame();
    }

    private static long delta(String name, long now, long last) {
        Tracy.plotInt(name, now - last);
        return now;
    }

    private static long deltaMem(String name, long now, long last) {
        Tracy.plotInt(name, now - last, TracyBackend.PLOT_FORMAT_MEMORY);
        return now;
    }
}
