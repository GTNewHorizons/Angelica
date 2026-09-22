package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.rendering.AngelicaRenderQueue;
import com.gtnewhorizons.angelica.rendering.FramePacer;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.rendering.celeritas.TerrainDrawStats;
import com.gtnewhorizons.angelica.rendering.culling.GpuCulling;
import com.gtnewhorizons.angelica.rendering.voxelization.SdlShadowVoxelizationSink;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.particles.ParticleInstancer;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.SharedQuadIndexBuffer;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;

public final class TracyFramePlots {
    private static final long P_ALLOC_RATE = Tracy.plotHandle("allocRate", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_HEAP_USED = Tracy.plotHandle("heapUsed", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final PlotDelta P_CHUNK_UPDATES = new PlotDelta("chunkUpdates");
    private static final long P_ENTITIES_RENDERED = Tracy.plotHandle("entitiesRendered");
    private static final long P_ENTITIES_TOTAL = Tracy.plotHandle("entitiesTotal");

    private static final PlotDelta P_GL_DRAW_CALLS = new PlotDelta("gl.drawCalls");
    private static final PlotDelta P_GL_TEX_BIND_MISSES = new PlotDelta("gl.texBindMisses");
    private static final PlotDelta P_GL_PROGRAM_SWITCHES = new PlotDelta("gl.programSwitches");
    private static final PlotDelta P_GLSM_BRACKETS = new PlotDelta("glsm.brackets");
    private static final PlotDelta P_GLSM_COW_SLOTS = new PlotDelta("glsm.cowSlots");
    private static final PlotDelta P_GLSM_POP_RESTORES = new PlotDelta("glsm.popRestores");
    private static final PlotDelta P_GLSM_POP_GL_CALLS = new PlotDelta("glsm.popGLCalls");
    private static final PlotDelta P_GLSM_POP_DISCARDS = new PlotDelta("glsm.popDiscards");
    private static final PlotDelta P_GL_LIST_PLAYBACKS = new PlotDelta("gl.listPlaybacks");
    private static final PlotDelta P_GL_STREAMED_BYTES = new PlotDelta("gl.streamedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final PlotDelta P_GL_STREAM_DRAWS = new PlotDelta("gl.streamDraws");
    private static final PlotDelta P_GL_ORPHAN_FALLBACKS = new PlotDelta("gl.orphanFallbacks");
    private static final PlotDelta P_GL_STREAM_CONTIGUOUS = new PlotDelta("gl.streamContiguous");
    private static final PlotDelta P_GL_RING_WRAPS = new PlotDelta("gl.ringWraps");
    private static final long P_GL_SECTION_META_BYTES = Tracy.plotHandle("gl.sectionMetaBytes", TracyBackend.PLOT_FORMAT_MEMORY);

    private static final long P_SHADOW_RELAY_FRAMES = Tracy.plotHandle("shadow.relayFrames");

    private static final long P_TERRAIN_DRAW_COMMANDS = Tracy.plotHandle("terrain.drawCommands");
    private static final long P_TERRAIN_REGIONS_DRAWN = Tracy.plotHandle("terrain.regionsDrawn");
    private static final long P_VOX_ENCODERS = Tracy.plotHandle("voxelization.encoders");
    private static final long P_VOX_DISPATCHES = Tracy.plotHandle("voxelization.dispatches");
    private static final long P_VOX_REGIONS = Tracy.plotHandle("voxelization.regions");
    private static final long P_DRAW_BATCH_REBUILDS = Tracy.plotHandle("draw.batchRebuilds");
    private static final PlotDelta P_DRAW_INDEX_BUFFER_GROWTHS = new PlotDelta("draw.indexBufferGrowths");

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
    private static final PlotDelta P_FFP_VARIANT_SWITCHES = new PlotDelta("ffp.variantSwitches");

    private static final PlotDelta P_TESR_REBUILDS = new PlotDelta("tesr.rebuilds");
    private static final PlotDelta P_TESR_RETAINED_DRAWS = new PlotDelta("tesr.retainedDraws");
    private static final PlotDelta P_TESR_INSTANCED_DRAWS = new PlotDelta("tesr.instancedDraws");
    private static final PlotDelta P_TESR_INSTANCED_INSTANCES = new PlotDelta("tesr.instancedInstances");
    private static final PlotDelta P_TESR_CUBE_INSTANCES = new PlotDelta("tesr.cubeInstances");
    private static final PlotDelta P_TESR_TEXMAT_RUNS = new PlotDelta("tesr.texMatrixRuns");
    private static final PlotDelta P_TESR_STREAMED_INSTANCES = new PlotDelta("tesr.streamedInstances");
    private static final PlotDelta P_TESR_VOLATILE_PROMOTIONS = new PlotDelta("tesr.volatilePromotions");
    private static final long P_TESR_RETAINED_BYTES = Tracy.plotHandle("tesr.retainedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long P_TESR_BUFFER_SOURCE_BYTES = Tracy.plotHandle("tesr.bufferSourceBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final PlotDelta P_TESR_CACHE_HITS = new PlotDelta("tesr.cacheHits");
    private static final PlotDelta P_TESR_CACHE_MISSES = new PlotDelta("tesr.cacheMisses");
    private static final PlotDelta P_TESR_MODEL_PARTS = new PlotDelta("tesr.modelParts");
    private static final PlotDelta P_TESR_LIVE_FALLBACKS = new PlotDelta("tesr.liveFallbacks");
    private static final PlotDelta P_ITEMS_INSTANCED = new PlotDelta("items.instanced");
    private static final PlotDelta P_ITEMS_FALLBACK = new PlotDelta("items.fallback");
    private static final PlotDelta P_ITEMS_GLINT_INSTANCED = new PlotDelta("items.glintInstanced");
    private static final PlotDelta P_PARTICLES_DIRECT = new PlotDelta("particles.direct");
    private static final PlotDelta P_PARTICLES_CAPTURED = new PlotDelta("particles.captured");
    private static final PlotDelta P_PARTICLES_SPILLED = new PlotDelta("particles.spilled");
    private static final PlotDelta P_PARTICLES_UNDECODABLE = new PlotDelta("particles.undecodable");
    private static final PlotDelta P_PARTICLES_DRAWS = new PlotDelta("particles.draws");
    private static final PlotDelta[] TESR_BAILS = bailHandles();
    private static final PlotDelta[] ITEM_BAILS = itemBailHandles();
    private static final PlotDelta P_ENTITY_SHADOW_QUADS = new PlotDelta("entity.shadowQuads");
    private static final PlotDelta P_ENTITY_SHADOW_DRAWS = new PlotDelta("entity.shadowDraws");

    private static final PlotDelta P_MESH_UPLOADED_SECTIONS = new PlotDelta("mesh.uploadedSections");
    private static final PlotDelta P_MESH_UPLOADED_BYTES = new PlotDelta("mesh.uploadedBytes", TracyBackend.PLOT_FORMAT_MEMORY);
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

    private static PlotDelta[] bailHandles() {
        final ModelPartBatcher.BailReason[] reasons = ModelPartBatcher.BailReason.VALUES;
        final PlotDelta[] out = new PlotDelta[reasons.length];
        for (int i = 0; i < reasons.length; i++) out[i] = new PlotDelta(reasons[i].plotName);
        return out;
    }

    private static PlotDelta[] itemBailHandles() {
        final DroppedItemInstancer.BailReason[] reasons = DroppedItemInstancer.BailReason.VALUES;
        final PlotDelta[] out = new PlotDelta[reasons.length];
        for (int i = 0; i < reasons.length; i++) out[i] = new PlotDelta(reasons[i].plotName);
        return out;
    }

    private TracyFramePlots() {}

    private static final long P_FRAME_GATE_US = Tracy.plotHandle("frameGateUs");
    private static final long P_PACER_GPU_WAIT_US = Tracy.plotHandle("pacer.gpuWaitUs");
    private static final long P_PACER_SLACK_US = Tracy.plotHandle("pacer.slackUs");
    private static final long P_PACER_SPIN_US = Tracy.plotHandle("pacer.spinUs");
    private static final long P_PACER_CEILING_HZ = Tracy.plotHandle("pacer.ceilingHz");
    private static final long P_PACER_MARGIN_US = Tracy.plotHandle("pacer.marginUs");
    private static final long P_PACER_LOCKED = Tracy.plotHandle("pacer.locked");
    private static final long P_PACER_PROBING = Tracy.plotHandle("pacer.probing");
    private static final long P_PACER_PRESENT_INTERVAL_US = Tracy.plotHandle("pacer.presentIntervalUs");
    private static final long P_PACER_IDLE_OVERSHOOT_US = Tracy.plotHandle("pacer.idleOvershootUs");

    public static void onFrame(Minecraft mc) {
        if (!Tracy.ENABLED) return;

        final boolean plots = CaptureGate.markersThisFrame;

        Tracy.plotAllocRate(P_ALLOC_RATE);
        Tracy.plotGcStats();
        if (plots) {
            final Runtime runtime = Runtime.getRuntime();
            Tracy.plotInt(P_HEAP_USED, runtime.totalMemory() - runtime.freeMemory());
        }

        Tracy.plotInt(P_FRAME_GATE_US, FramePacer.gateDurationNanos() / 1000L);
        Tracy.plotInt(P_PACER_GPU_WAIT_US, FramePacer.gpuWaitNanos() / 1000L);
        Tracy.plotInt(P_PACER_SLACK_US, FramePacer.slackNanos() / 1000L);
        Tracy.plotInt(P_PACER_SPIN_US, FramePacer.spinNanos() / 1000L);
        Tracy.plotInt(P_PACER_MARGIN_US, FramePacer.marginNanos() / 1000L);
        Tracy.plotInt(P_PACER_PRESENT_INTERVAL_US, FramePacer.presentIntervalNanos() / 1000L);
        Tracy.plotInt(P_PACER_IDLE_OVERSHOOT_US, FramePacer.idleOvershootNanos() / 1000L);
        Tracy.plotInt(P_PACER_CEILING_HZ, FramePacer.effectiveCapHz());
        Tracy.plotInt(P_PACER_LOCKED, FramePacer.locked() ? 1 : 0);
        Tracy.plotInt(P_PACER_PROBING, FramePacer.probing() ? 1 : 0);

        P_CHUNK_UPDATES.plot(WorldRenderer.chunksUpdated);
        if (mc.renderGlobal != null) {
            Tracy.plotInt(P_ENTITIES_RENDERED, mc.renderGlobal.countEntitiesRendered);
        }
        if (mc.theWorld != null) {
            Tracy.plotInt(P_ENTITIES_TOTAL, mc.theWorld.loadedEntityList.size());
        }

        P_GL_DRAW_CALLS.plot(GLStateManager.drawCalls);
        P_GL_TEX_BIND_MISSES.plot(GLStateManager.texBindMisses);
        P_GL_PROGRAM_SWITCHES.plot(GLStateManager.programSwitches);
        P_GLSM_BRACKETS.plot(GLStateManager.attribPushes);
        P_GLSM_COW_SLOTS.plot(GLStateManager.attribSlotsSaved);
        P_GLSM_POP_RESTORES.plot(GLStateManager.attribValueRestores);
        P_GLSM_POP_GL_CALLS.plot(GLStateManager.attribBackendCalls);
        P_GLSM_POP_DISCARDS.plot(GLStateManager.attribDiscards);
        P_GL_LIST_PLAYBACKS.plot(DisplayListManager.listPlaybacks);
        P_GL_STREAMED_BYTES.plot(TessellatorStreamingDrawer.streamedBytes);
        P_GL_STREAM_DRAWS.plot(TessellatorStreamingDrawer.streamDraws);
        P_GL_ORPHAN_FALLBACKS.plot(TessellatorStreamingDrawer.orphanFallbacks);
        P_GL_STREAM_CONTIGUOUS.plot(TessellatorStreamingDrawer.streamContiguous);
        P_GL_RING_WRAPS.plot(TessellatorStreamingDrawer.ringWraps());
        Tracy.plotInt(P_GL_SECTION_META_BYTES, TerrainDrawStats.takeSectionMetaBytes());
        Tracy.plotInt(P_SHADOW_RELAY_FRAMES, ShadowRenderer.SHADOW_TERRAIN_RELAID ? 1 : 0);
        Tracy.plotInt(P_TERRAIN_DRAW_COMMANDS, TerrainDrawStats.takeCommandsSubmitted());
        Tracy.plotInt(P_TERRAIN_REGIONS_DRAWN, TerrainDrawStats.takeRegionsDrawn());
        Tracy.plotInt(P_DRAW_BATCH_REBUILDS, TerrainDrawStats.takeBatchRebuilds());
        Tracy.plotInt(P_VOX_ENCODERS, SdlShadowVoxelizationSink.takeEncoders());
        Tracy.plotInt(P_VOX_DISPATCHES, SdlShadowVoxelizationSink.takeDispatches());
        Tracy.plotInt(P_VOX_REGIONS, SdlShadowVoxelizationSink.takeRegions());
        Tracy.plotInt(P_CULL_REGIONS_DRAWN, TerrainDrawStats.takeCullRegionsDrawn());
        Tracy.plotInt(P_CULL_MAX_REGION_COMMANDS, TerrainDrawStats.takeCullMaxRegionCommands());
        Tracy.plotInt(P_CULL_COMMANDS_SUBMITTED, TerrainDrawStats.takeCullCommandsSubmitted());

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
        P_FFP_VARIANT_SWITCHES.plot(ShaderManager.variantSwitches);

        final TesrBatchRenderer tesr = TesrBatchRenderer.INSTANCE;
        final ModelPartBatcher batcher = ModelPartBatcher.INSTANCE;

        P_TESR_REBUILDS.plot(tesr.statRebuilds());
        P_TESR_RETAINED_DRAWS.plot(tesr.statRetainedDraws());
        P_TESR_INSTANCED_DRAWS.plot(tesr.statInstancedDraws() + batcher.statInstancedDraws());
        P_TESR_INSTANCED_INSTANCES.plot(tesr.statInstancedInstances() + batcher.statInstancedInstances());
        P_TESR_CUBE_INSTANCES.plot(batcher.statCubeInstances());
        P_TESR_TEXMAT_RUNS.plot(batcher.statTexMatrixRuns());
        P_TESR_STREAMED_INSTANCES.plot(tesr.statStreamedInstances());
        P_TESR_VOLATILE_PROMOTIONS.plot(tesr.statVolatilePromotions());
        if (plots) {
            Tracy.plotInt(P_TESR_RETAINED_BYTES, tesr.statRetainedBytes());
            Tracy.plotInt(P_TESR_BUFFER_SOURCE_BYTES, tesr.statBufferSourceBytes());
        }
        P_TESR_CACHE_HITS.plot(AngelicaTesrMeshCache.cacheHits);
        P_TESR_CACHE_MISSES.plot(AngelicaTesrMeshCache.cacheMisses);
        P_TESR_MODEL_PARTS.plot(batcher.statParts());
        P_TESR_LIVE_FALLBACKS.plot(batcher.statLiveFallbacks());
        P_ITEMS_INSTANCED.plot(DroppedItemInstancer.statInstanced());
        P_ITEMS_FALLBACK.plot(DroppedItemInstancer.statFallback());
        P_ITEMS_GLINT_INSTANCED.plot(DroppedItemInstancer.statGlintInstanced());
        for (final DroppedItemInstancer.BailReason reason : DroppedItemInstancer.BailReason.VALUES) {
            ITEM_BAILS[reason.ordinal()].plot(DroppedItemInstancer.statBail(reason));
        }
        P_PARTICLES_DIRECT.plot(ParticleInstancer.getDirect());
        P_PARTICLES_CAPTURED.plot(ParticleInstancer.getCaptured());
        P_PARTICLES_SPILLED.plot(ParticleInstancer.getSpilled());
        P_PARTICLES_UNDECODABLE.plot(ParticleInstancer.getUndecodable());
        P_PARTICLES_DRAWS.plot(ParticleInstancer.getDraws());
        P_ENTITY_SHADOW_QUADS.plot(batcher.statShadowQuads());
        P_ENTITY_SHADOW_DRAWS.plot(batcher.statShadowDraws());
        for (final ModelPartBatcher.BailReason reason : ModelPartBatcher.BailReason.VALUES) {
            TESR_BAILS[reason.ordinal()].plot(batcher.statBail(reason));
        }

        P_MESH_UPLOADED_SECTIONS.plot(RenderRegionManager.getSectionsUploaded());
        P_MESH_UPLOADED_BYTES.plot(RenderRegionManager.getBytesUploaded());
        P_DRAW_INDEX_BUFFER_GROWTHS.plot(SharedQuadIndexBuffer.getGrowths());
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
        BailClassCounts.PARTICLE_SPILL.flushFrame(plots);
    }

    private static final class PlotDelta {
        private final long handle;
        private long last;

        PlotDelta(String name) {
            this(name, TracyBackend.PLOT_FORMAT_NUMBER);
        }

        PlotDelta(String name, int format) {
            handle = Tracy.plotHandle(name, format);
        }

        void plot(long now) {
            Tracy.plotInt(handle, now - last);
            last = now;
        }
    }
}
