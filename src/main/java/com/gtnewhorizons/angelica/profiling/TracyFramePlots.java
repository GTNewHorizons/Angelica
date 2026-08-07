package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.culling.GpuIndirectMultiDrawEmitter;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.rendering.celeritas.TerrainDrawStats;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import com.gtnewhorizons.angelica.rendering.voxelization.SdlShadowVoxelizationSink;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;

public final class TracyFramePlots {
    private static long lastChunkUpdates;
    private static long lastDrawCalls, lastTexBindMisses, lastProgramSwitches, lastListPlaybacks;
    private static long lastStreamedBytes, lastStreamDraws, lastOrphanFallbacks, lastStreamContiguous;
    private static long lastSectionMetaBytes, lastRingWraps, lastVariantSwitches;
    private static long lastTesrRebuilds, lastTesrRetainedDraws, lastTesrInstDraws, lastTesrInstInstances;
    private static long lastTesrStreamedInstances, lastTesrPromotions, lastTesrCacheHits, lastTesrCacheMisses;
    private static long lastBatcherParts, lastBatcherLiveFallbacks;
    private static final long[] lastBails = new long[ModelPartBatcher.BailReason.VALUES.length];
    private static long lastSectionsUploaded, lastBytesUploaded;
    private static int lastCullSubmitted;

    private static final long P_ALLOC_RATE = Tracy.plotHandle("allocRate", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_HEAP_USED = Tracy.plotHandle("heapUsed", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_CHUNK_UPDATES = Tracy.plotHandle("chunkUpdates");
    private static final long P_ENTITIES_RENDERED = Tracy.plotHandle("entitiesRendered");
    private static final long P_ENTITIES_TOTAL = Tracy.plotHandle("entitiesTotal");

    private static final long P_GL_DRAW_CALLS = Tracy.plotHandle("gl.drawCalls");
    private static final long P_GL_TEX_BIND_MISSES = Tracy.plotHandle("gl.texBindMisses");
    private static final long P_GL_PROGRAM_SWITCHES = Tracy.plotHandle("gl.programSwitches");
    private static final long P_GL_LIST_PLAYBACKS = Tracy.plotHandle("gl.listPlaybacks");
    private static final long P_GL_STREAMED_BYTES = Tracy.plotHandle("gl.streamedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_GL_STREAM_DRAWS = Tracy.plotHandle("gl.streamDraws");
    private static final long P_GL_ORPHAN_FALLBACKS = Tracy.plotHandle("gl.orphanFallbacks");
    private static final long P_GL_STREAM_CONTIGUOUS = Tracy.plotHandle("gl.streamContiguous");
    private static final long P_GL_RING_WRAPS = Tracy.plotHandle("gl.ringWraps");
    private static final long P_GL_SECTION_META_BYTES = Tracy.plotHandle("gl.sectionMetaBytes", TracyBackend.PLOT_FORMAT_MEMORY);

    private static final long P_SHADOW_RELAY_FRAMES = Tracy.plotHandle("shadow.relayFrames");

    private static final long P_TERRAIN_DRAW_COMMANDS = Tracy.plotHandle("terrain.drawCommands");
    private static final long P_TERRAIN_REGIONS_DRAWN = Tracy.plotHandle("terrain.regionsDrawn");
    private static final long P_VOX_ENCODERS = Tracy.plotHandle("voxelization.encoders");
    private static final long P_VOX_DISPATCHES = Tracy.plotHandle("voxelization.dispatches");
    private static final long P_VOX_REGIONS = Tracy.plotHandle("voxelization.regions");
    private static final long P_DRAW_ARENA_GROWTHS = Tracy.plotHandle("draw.arenaGrowths");
    private static final long P_DRAW_INDEX_BUFFER_GROWTHS = Tracy.plotHandle("draw.indexBufferGrowths");

    private static final long P_CULL_COMMANDS_SUBMITTED = Tracy.plotHandle("cull.commandsSubmitted");
    private static final long P_CULL_REGIONS_DRAWN = Tracy.plotHandle("cull.regionsDrawn");
    private static final long P_CULL_MAX_REGION_COMMANDS = Tracy.plotHandle("cull.maxRegionCommands");

    private static final long P_FFP_PRE_DRAW_CALLS = Tracy.plotHandle("ffp.preDrawCalls");
    private static final long P_FFP_BLOCK_WRITES = Tracy.plotHandle("ffp.blockWrites");
    private static final long P_FFP_BLOCK_SKIPS = Tracy.plotHandle("ffp.blockSkips");
    private static final long P_FFP_STAGED_MATRICES = Tracy.plotHandle("ffp.stagedMatrices");
    private static final long P_FFP_STAGED_LIGHTING = Tracy.plotHandle("ffp.stagedLighting");
    private static final long P_FFP_STAGED_FRAGMENT = Tracy.plotHandle("ffp.stagedFragment");
    private static final long P_FFP_STAGED_COLOR = Tracy.plotHandle("ffp.stagedColor");
    private static final long P_FFP_STAGED_NORMAL = Tracy.plotHandle("ffp.stagedNormal");
    private static final long P_FFP_STAGED_TEX_COORD = Tracy.plotHandle("ffp.stagedTexCoord");
    private static final long P_FFP_STAGED_LIGHTMAP = Tracy.plotHandle("ffp.stagedLightmap");
    private static final long P_FFP_STAGED_TEX_GEN = Tracy.plotHandle("ffp.stagedTexGen");
    private static final long P_FFP_STAGED_CLIP_PLANES = Tracy.plotHandle("ffp.stagedClipPlanes");
    private static final long P_FFP_STAGED_MISC = Tracy.plotHandle("ffp.stagedMisc");
    private static final long P_FFP_PROGRAMS = Tracy.plotHandle("ffp.programs");
    private static final long P_FFP_VARIANT_SWITCHES = Tracy.plotHandle("ffp.variantSwitches");

    private static final long P_TESR_REBUILDS = Tracy.plotHandle("tesr.rebuilds");
    private static final long P_TESR_RETAINED_DRAWS = Tracy.plotHandle("tesr.retainedDraws");
    private static final long P_TESR_INSTANCED_DRAWS = Tracy.plotHandle("tesr.instancedDraws");
    private static final long P_TESR_INSTANCED_INSTANCES = Tracy.plotHandle("tesr.instancedInstances");
    private static final long P_TESR_STREAMED_INSTANCES = Tracy.plotHandle("tesr.streamedInstances");
    private static final long P_TESR_VOLATILE_PROMOTIONS = Tracy.plotHandle("tesr.volatilePromotions");
    private static final long P_TESR_RETAINED_BYTES = Tracy.plotHandle("tesr.retainedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_TESR_BUFFER_SOURCE_BYTES = Tracy.plotHandle("tesr.bufferSourceBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_TESR_CACHE_HITS = Tracy.plotHandle("tesr.cacheHits");
    private static final long P_TESR_CACHE_MISSES = Tracy.plotHandle("tesr.cacheMisses");
    private static final long P_TESR_MODEL_PARTS = Tracy.plotHandle("tesr.modelParts");
    private static final long P_TESR_LIVE_FALLBACKS = Tracy.plotHandle("tesr.liveFallbacks");
    private static final long[] P_TESR_BAILS = bailHandles();

    private static final long P_MESH_UPLOADED_SECTIONS = Tracy.plotHandle("mesh.uploadedSections");
    private static final long P_MESH_UPLOADED_BYTES = Tracy.plotHandle("mesh.uploadedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_MESH_META_SLOTS = Tracy.plotHandle("mesh.metaSlots");
    private static final long P_MT_QUEUE_DEPTH = Tracy.plotHandle("mtQueue.depth");
    private static final long P_MT_QUEUE_TASKS = Tracy.plotHandle("mtQueue.tasksRan");
    private static final long P_MT_QUEUE_LONGEST_US = Tracy.plotHandle("mtQueue.longestTaskUs");

    private static final long P_QUEUE_DEPTH = Tracy.plotHandle("queue.depth");
    private static final long P_QUEUE_TASKS_RAN = Tracy.plotHandle("queue.tasksRan");
    private static final long P_QUEUE_TIME_NS = Tracy.plotHandle("queue.timeNs");

    private static final long P_DYN_LIGHT_SOURCES = Tracy.plotHandle("dyn.lightSources");
    private static final long P_DYN_UPDATES = Tracy.plotHandle("dyn.updates");
    private static final long P_DYN_PENDING_REBUILDS = Tracy.plotHandle("dyn.pendingRebuilds");

    private static long[] bailHandles() {
        final ModelPartBatcher.BailReason[] reasons = ModelPartBatcher.BailReason.VALUES;
        final long[] out = new long[reasons.length];
        for (int i = 0; i < reasons.length; i++) out[i] = Tracy.plotHandle(reasons[i].plotName);
        return out;
    }

    private TracyFramePlots() {}

    public static void onFrame(Minecraft mc) {
        if (!Tracy.ENABLED) return;

        final boolean plots = CaptureGate.markersThisFrame;

        Tracy.plotAllocRate(P_ALLOC_RATE);
        Tracy.plotGcStats();
        if (plots) {
            final Runtime runtime = Runtime.getRuntime();
            Tracy.plotInt(P_HEAP_USED, runtime.totalMemory() - runtime.freeMemory());
        }
        final int chunksUpdated = WorldRenderer.chunksUpdated;
        Tracy.plotInt(P_CHUNK_UPDATES, chunksUpdated - lastChunkUpdates);
        lastChunkUpdates = chunksUpdated;
        if (mc.renderGlobal != null) {
            Tracy.plotInt(P_ENTITIES_RENDERED, mc.renderGlobal.countEntitiesRendered);
        }
        if (mc.theWorld != null) {
            Tracy.plotInt(P_ENTITIES_TOTAL, mc.theWorld.loadedEntityList.size());
        }

        lastDrawCalls = delta(P_GL_DRAW_CALLS, GLStateManager.drawCalls, lastDrawCalls);
        lastTexBindMisses = delta(P_GL_TEX_BIND_MISSES, GLStateManager.texBindMisses, lastTexBindMisses);
        lastProgramSwitches = delta(P_GL_PROGRAM_SWITCHES, GLStateManager.programSwitches, lastProgramSwitches);
        lastListPlaybacks = delta(P_GL_LIST_PLAYBACKS, DisplayListManager.listPlaybacks, lastListPlaybacks);
        lastStreamedBytes = delta(P_GL_STREAMED_BYTES, TessellatorStreamingDrawer.streamedBytes, lastStreamedBytes);
        lastStreamDraws = delta(P_GL_STREAM_DRAWS, TessellatorStreamingDrawer.streamDraws, lastStreamDraws);
        lastOrphanFallbacks = delta(P_GL_ORPHAN_FALLBACKS, TessellatorStreamingDrawer.orphanFallbacks, lastOrphanFallbacks);
        lastStreamContiguous = delta(P_GL_STREAM_CONTIGUOUS, TessellatorStreamingDrawer.streamContiguous, lastStreamContiguous);
        lastRingWraps = delta(P_GL_RING_WRAPS, TessellatorStreamingDrawer.ringWraps(), lastRingWraps);
        lastSectionMetaBytes = delta(P_GL_SECTION_META_BYTES, GpuIndirectMultiDrawEmitter.sectionMetaBytes, lastSectionMetaBytes);
        Tracy.plotInt(P_SHADOW_RELAY_FRAMES, ShadowRenderer.SHADOW_TERRAIN_RELAID ? 1 : 0);
        Tracy.plotInt(P_TERRAIN_DRAW_COMMANDS, TerrainDrawStats.takeCommandsSubmitted());
        Tracy.plotInt(P_TERRAIN_REGIONS_DRAWN, TerrainDrawStats.takeRegionsDrawn());
        Tracy.plotInt(P_DRAW_ARENA_GROWTHS, IndirectMultiDrawEmitter.takeArenaGrowths());
        Tracy.plotInt(P_DRAW_INDEX_BUFFER_GROWTHS, SharedQuadIndexBuffer.takeGrowths());
        Tracy.plotInt(P_VOX_ENCODERS, SdlShadowVoxelizationSink.takeEncoders());
        Tracy.plotInt(P_VOX_DISPATCHES, SdlShadowVoxelizationSink.takeDispatches());
        Tracy.plotInt(P_VOX_REGIONS, SdlShadowVoxelizationSink.takeRegions());
        Tracy.plotInt(P_CULL_REGIONS_DRAWN, GpuIndirectMultiDrawEmitter.takeRegionsDrawn());
        Tracy.plotInt(P_CULL_MAX_REGION_COMMANDS, GpuIndirectMultiDrawEmitter.takeMaxRegionCommands());
        final int submitted = GpuIndirectMultiDrawEmitter.commandsSubmitted();
        Tracy.plotInt(P_CULL_COMMANDS_SUBMITTED, submitted - lastCullSubmitted);
        lastCullSubmitted = submitted;

        final ShaderManager ffp = ShaderManager.getInstance();
        Tracy.plotInt(P_FFP_PRE_DRAW_CALLS, ffp.statLastFramePreDrawCalls());
        Tracy.plotInt(P_FFP_BLOCK_WRITES, ffp.statLastFrameBlockWrites());
        Tracy.plotInt(P_FFP_BLOCK_SKIPS, ffp.statLastFrameBlockSkips());
        Tracy.plotInt(P_FFP_STAGED_MATRICES, ffp.statLastFrameStagedMatrices());
        Tracy.plotInt(P_FFP_STAGED_LIGHTING, ffp.statLastFrameStagedLighting());
        Tracy.plotInt(P_FFP_STAGED_FRAGMENT, ffp.statLastFrameStagedFragment());
        Tracy.plotInt(P_FFP_STAGED_COLOR, ffp.statLastFrameStagedColor());
        Tracy.plotInt(P_FFP_STAGED_NORMAL, ffp.statLastFrameStagedNormal());
        Tracy.plotInt(P_FFP_STAGED_TEX_COORD, ffp.statLastFrameStagedTexCoord());
        Tracy.plotInt(P_FFP_STAGED_LIGHTMAP, ffp.statLastFrameStagedLightmap());
        Tracy.plotInt(P_FFP_STAGED_TEX_GEN, ffp.statLastFrameStagedTexGen());
        Tracy.plotInt(P_FFP_STAGED_CLIP_PLANES, ffp.statLastFrameStagedClipPlanes());
        Tracy.plotInt(P_FFP_STAGED_MISC, ffp.statLastFrameStagedMisc());
        Tracy.plotInt(P_FFP_PROGRAMS, ffp.statProgramCount());
        lastVariantSwitches = delta(P_FFP_VARIANT_SWITCHES, ShaderManager.variantSwitches, lastVariantSwitches);

        final TesrBatchRenderer tesr = TesrBatchRenderer.INSTANCE;
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;

        lastTesrRebuilds = delta(P_TESR_REBUILDS, tesr.statRebuilds(), lastTesrRebuilds);
        lastTesrRetainedDraws = delta(P_TESR_RETAINED_DRAWS, tesr.statRetainedDraws(), lastTesrRetainedDraws);
        lastTesrInstDraws = delta(P_TESR_INSTANCED_DRAWS, tesr.statInstancedDraws(), lastTesrInstDraws);
        lastTesrInstInstances = delta(P_TESR_INSTANCED_INSTANCES, tesr.statInstancedInstances(), lastTesrInstInstances);
        lastTesrStreamedInstances = delta(P_TESR_STREAMED_INSTANCES, tesr.statStreamedInstances(), lastTesrStreamedInstances);
        lastTesrPromotions = delta(P_TESR_VOLATILE_PROMOTIONS, tesr.statVolatilePromotions(), lastTesrPromotions);
        if (plots) {
            Tracy.plotInt(P_TESR_RETAINED_BYTES, tesr.statRetainedBytes());
            Tracy.plotInt(P_TESR_BUFFER_SOURCE_BYTES, tesr.statBufferSourceBytes());
        }
        lastTesrCacheHits = delta(P_TESR_CACHE_HITS, AngelicaTesrMeshCache.cacheHits, lastTesrCacheHits);
        lastTesrCacheMisses = delta(P_TESR_CACHE_MISSES, AngelicaTesrMeshCache.cacheMisses, lastTesrCacheMisses);
        lastBatcherParts = delta(P_TESR_MODEL_PARTS, batcher.statParts(), lastBatcherParts);
        lastBatcherLiveFallbacks = delta(P_TESR_LIVE_FALLBACKS, batcher.statLiveFallbacks(), lastBatcherLiveFallbacks);
        for (final ModelPartBatcher.BailReason reason : ModelPartBatcher.BailReason.VALUES) {
            final int i = reason.ordinal();
            lastBails[i] = delta(P_TESR_BAILS[i], batcher.statBail(reason), lastBails[i]);
        }

        lastSectionsUploaded = delta(P_MESH_UPLOADED_SECTIONS, RenderRegionManager.getSectionsUploaded(), lastSectionsUploaded);
        lastBytesUploaded = delta(P_MESH_UPLOADED_BYTES, RenderRegionManager.getBytesUploaded(), lastBytesUploaded);
        Tracy.plotInt(P_MESH_META_SLOTS, GpuCulling.sectionMeta().getHighWaterMark());
        Tracy.plotInt(P_MT_QUEUE_DEPTH, AngelicaRenderQueue.getQueueDepth());
        Tracy.plotInt(P_MT_QUEUE_TASKS, AngelicaRenderQueue.getLastFrameTasksRan());
        Tracy.plotInt(P_MT_QUEUE_LONGEST_US, AngelicaRenderQueue.getLastFrameLongestTaskNs() / 1000);
        final CeleritasWorldRenderer cwr = CeleritasWorldRenderer.getInstanceOrNull();
        if (plots && cwr != null && cwr.isActive()) {
            cwr.getRenderSectionManager().tracyPlots();
        }

        Tracy.plotInt(P_QUEUE_DEPTH, AngelicaRenderQueue.getQueueDepth());
        Tracy.plotInt(P_QUEUE_TASKS_RAN, AngelicaRenderQueue.getLastFrameTasksRan());
        Tracy.plotInt(P_QUEUE_TIME_NS, AngelicaRenderQueue.getLastFrameTimeNs());

        if (DynamicLights.isEnabled()) {
            final DynamicLights dl = DynamicLights.get();
            Tracy.plotInt(P_DYN_LIGHT_SOURCES, dl.getLightSourcesCount());
            Tracy.plotInt(P_DYN_UPDATES, dl.getLastUpdateCount());
            if (DynamicLights.FrustumCullingEnabled) {
                Tracy.plotInt(P_DYN_PENDING_REBUILDS, dl.getChunkRebuildManager().getPendingCount());
            }
        }

        RenderClassTimings.ENTITY.flushFrame(plots);
        RenderClassTimings.SHADOW_ENTITY.flushFrame(plots);
        RenderClassTimings.TESR.flushFrame(plots);
        BailClassCounts.MATERIAL.flushFrame(plots);
        BailClassCounts.TEMPLATE.flushFrame(plots);
    }

    private static long delta(long handle, long now, long last) {
        Tracy.plotInt(handle, now - last);
        return now;
    }
}
