package com.gtnewhorizons.angelica.sdlgpu.frame;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.profiling.TracyBackend;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;

final class SdlFramePlots {

    private SdlFramePlots() {}

    private static final long PLOT_RENDER_PASSES = Tracy.plotHandle("sdl.renderPasses");
    private static final long PLOT_CLEAR_PASSES = Tracy.plotHandle("sdl.clearPasses");
    private static final long PLOT_CLEAR_PASSES_MATERIALIZED = Tracy.plotHandle("sdl.clearPasses.materialized");
    private static final long PLOT_PASSES_SWAPCHAIN = Tracy.plotHandle("sdl.passes.swapchain");
    private static final long PLOT_PASSES_FBO = Tracy.plotHandle("sdl.passes.fbo");
    private static final long PLOT_LOCKED_HANDLE_READS = Tracy.plotHandle("sdl.lockedHandleReads");
    private static final long PLOT_SLOT_WRITES = Tracy.plotHandle("sdl.slotWrites");
    private static final long PLOT_SLOT_WRITES_ELIDED = Tracy.plotHandle("sdl.slotWritesElided");
    private static final long PLOT_COMPUTE_PASSES = Tracy.plotHandle("sdl.computePasses");
    private static final long PLOT_SUBMITS = Tracy.plotHandle("sdl.submits");
    private static final long PLOT_UNIFORM_PUSH_BYTES = Tracy.plotHandle("sdl.uniformPushBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long PLOT_UNIFORM_PUSHES = Tracy.plotHandle("sdl.uniformPushes");
    private static final long PLOT_UNIFORM_PUSH_SKIPS = Tracy.plotHandle("sdl.uniformPushSkips");
    private static final long PLOT_EXT_UBO_PUSHES = Tracy.plotHandle("sdl.extUboPushes");
    private static final long PLOT_EXT_UBO_PUSH_SKIPS = Tracy.plotHandle("sdl.extUboPushSkips");
    private static final long PLOT_PERSISTENT_DRAINS = Tracy.plotHandle("sdl.persistentDrains");
    private static final long PLOT_PER_FRAME_BLOCK_FLUSHES = Tracy.plotHandle("sdl.perFrameBlockFlushes");
    private static final long PLOT_PER_FRAME_BLOCK_PASS_BREAKS = Tracy.plotHandle("sdl.perFrameBlockPassBreaks");
    private static final long PLOT_BATCH_BREAK_PIPELINE = Tracy.plotHandle("sdl.batchBreaks.pipeline");
    private static final long PLOT_BATCH_BREAK_SAMPLER = Tracy.plotHandle("sdl.batchBreaks.sampler");
    private static final long PLOT_STATE_APPLIES = Tracy.plotHandle("sdl.stateApplies");
    private static final long PLOT_PER_FRAME_BLOCK_BYTES = Tracy.plotHandle("sdl.perFrameBlockBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long PLOT_PER_PASS_BLOCK_FLUSHES = Tracy.plotHandle("sdl.perPassBlockFlushes");
    private static final long PLOT_PER_PASS_BLOCK_BYTES = Tracy.plotHandle("sdl.perPassBlockBytes", TracyBackend.PLOT_FORMAT_MEMORY);
    private static final long PLOT_ACQUIRE_WAIT_US = Tracy.plotHandle("sdl.acquireWaitUs");
    private static final long PLOT_PRESENT_TO_PRESENT_US = Tracy.plotHandle("sdl.presentToPresentUs");
    private static final long PLOT_FRAMES_IN_FLIGHT = Tracy.plotHandle("sdl.framesInFlight");
    private static final long PLOT_MIP_GENS = Tracy.plotHandle("sdl.mipGens");
    private static final long PLOT_MIP_GEN_SUBMITS = Tracy.plotHandle("sdl.mipGenSubmits");
    private static final long PLOT_UPLOAD_FLUSH_SUBMITS = Tracy.plotHandle("sdl.uploadFlushSubmits");
    private static final long PLOT_BLIT_FALLBACKS = Tracy.plotHandle("sdl.blitFallbacks");
    private static final long PLOT_PRESENT_SKIPS = Tracy.plotHandle("sdl.presentSkips");
    private static final long PLOT_EMPTY_FRAMES = Tracy.plotHandle("sdl.emptyFrames");
    private static final long PLOT_DROPPED_DRAWS = Tracy.plotHandle("sdl.droppedDraws");
    private static final long PLOT_SSBO_BINDS = Tracy.plotHandle("sdl.ssboBinds");
    private static final long PLOT_COMPUTE_BATCH_JOINS = Tracy.plotHandle("sdl.computeBatchJoins");
    private static final long PLOT_ARENA_SUBMITS = Tracy.plotHandle("sdl.arenaSubmits");
    private static final long PLOT_ARENA_OVERFLOW_FLUSHES = Tracy.plotHandle("sdl.arenaOverflowFlushes");
    private static final long PLOT_COPY_PASSES = Tracy.plotHandle("sdl.copyPasses");
    private static final long PLOT_BUFFER_CREATES = Tracy.plotHandle("sdl.bufferCreates");
    private static final long PLOT_BUFFER_DELETES = Tracy.plotHandle("sdl.bufferDeletes");
    private static final long PLOT_BUFFER_POOL_HITS = Tracy.plotHandle("sdl.bufferPoolHits");
    private static final long PLOT_PIPELINE_KEY_RECOMPUTES = Tracy.plotHandle("sdl.pipelineKeyRecomputes");
    private static final long PLOT_PIPELINE_FAST_PATH_HITS = Tracy.plotHandle("sdl.pipelineFastPathHits");
    private static final long PLOT_MID_FRAME_SUBMITS = Tracy.plotHandle("sdl.midFrameSubmits");

    private static final int[] contextCounters = new int[SDLGPURenderBackend.CTR_COUNT];

    static void emit(FrameState f, ResourceManager resourceManager) {
        SDLGPURenderBackend.takeContextCounters(contextCounters);
        Tracy.plotInt(PLOT_RENDER_PASSES, f.renderPassGeneration - f.lastPlottedRenderPassGen - f.clearPassesThisFrame);
        Tracy.plotInt(PLOT_CLEAR_PASSES, f.clearPassesThisFrame);
        Tracy.plotInt(PLOT_CLEAR_PASSES_MATERIALIZED, f.materializedClearPassesThisFrame);
        Tracy.plotInt(PLOT_PASSES_SWAPCHAIN, f.swapchainPassesThisFrame);
        Tracy.plotInt(PLOT_PASSES_FBO, f.fboPassesThisFrame - f.clearPassesThisFrame);
        Tracy.plotInt(PLOT_LOCKED_HANDLE_READS, resourceManager.takeLockedReadCount());
        Tracy.plotInt(PLOT_SLOT_WRITES, contextCounters[SDLGPURenderBackend.CTR_SLOT_WRITES]);
        Tracy.plotInt(PLOT_SLOT_WRITES_ELIDED, contextCounters[SDLGPURenderBackend.CTR_SLOT_WRITES_ELIDED]);
        Tracy.plotInt(PLOT_SSBO_BINDS, contextCounters[SDLGPURenderBackend.CTR_SSBO_BINDS]);
        Tracy.plotInt(PLOT_ARENA_SUBMITS, f.arenaSubmitsThisFrame);
        Tracy.plotInt(PLOT_ARENA_OVERFLOW_FLUSHES, f.arenaOverflowFlushesThisFrame);
        Tracy.plotInt(PLOT_COPY_PASSES, f.copyPassesThisFrame);
        Tracy.plotInt(PLOT_BUFFER_CREATES, resourceManager.takeBufferCreateCount());
        Tracy.plotInt(PLOT_BUFFER_DELETES, resourceManager.takeBufferDeleteCount());
        Tracy.plotInt(PLOT_BUFFER_POOL_HITS, resourceManager.takeBufferPoolHitCount());
        Tracy.plotInt(PLOT_PIPELINE_KEY_RECOMPUTES, contextCounters[SDLGPURenderBackend.CTR_KEY_RECOMPUTES]);
        Tracy.plotInt(PLOT_PIPELINE_FAST_PATH_HITS, contextCounters[SDLGPURenderBackend.CTR_FAST_PATH_HITS]);
        Tracy.plotInt(PLOT_COMPUTE_PASSES, f.computePassesThisFrame);
        Tracy.plotInt(PLOT_COMPUTE_BATCH_JOINS, f.computeBatchJoinsThisFrame);
        Tracy.plotInt(PLOT_SUBMITS, f.submitsThisFrame);
        Tracy.plotInt(PLOT_UNIFORM_PUSH_BYTES, f.uniformPushBytesThisFrame);
        Tracy.plotInt(PLOT_UNIFORM_PUSHES, f.uniformPushesThisFrame);
        Tracy.plotInt(PLOT_UNIFORM_PUSH_SKIPS, f.uniformPushSkipsThisFrame);
        Tracy.plotInt(PLOT_EXT_UBO_PUSHES, f.externalUboPushesThisFrame);
        Tracy.plotInt(PLOT_EXT_UBO_PUSH_SKIPS, f.externalUboPushSkipsThisFrame);
        Tracy.plotInt(PLOT_PERSISTENT_DRAINS, f.persistentDrainsThisFrame);
        Tracy.plotInt(PLOT_PER_FRAME_BLOCK_FLUSHES, f.uniformBlockFlushesThisFrame[ShaderManager.BLOCK_PER_FRAME]);
        Tracy.plotInt(PLOT_PER_FRAME_BLOCK_PASS_BREAKS, f.perFrameBlockPassBreaksThisFrame);
        Tracy.plotInt(PLOT_BATCH_BREAK_PIPELINE, f.batchBreakPipelineThisFrame);
        Tracy.plotInt(PLOT_BATCH_BREAK_SAMPLER, f.batchBreakSamplerThisFrame);
        Tracy.plotInt(PLOT_STATE_APPLIES, f.stateAppliesThisFrame);
        Tracy.plotInt(PLOT_PER_FRAME_BLOCK_BYTES, f.uniformBlockBytesThisFrame[ShaderManager.BLOCK_PER_FRAME]);
        Tracy.plotInt(PLOT_PER_PASS_BLOCK_FLUSHES, f.uniformBlockFlushesThisFrame[ShaderManager.BLOCK_PER_PASS]);
        Tracy.plotInt(PLOT_PER_PASS_BLOCK_BYTES, f.uniformBlockBytesThisFrame[ShaderManager.BLOCK_PER_PASS]);
        Tracy.plotInt(PLOT_ACQUIRE_WAIT_US, f.acquireWaitNanosThisFrame / 1000);
        final long nowNanos = System.nanoTime();
        if (f.lastEndFrameNanos != 0) Tracy.plotInt(PLOT_PRESENT_TO_PRESENT_US, (nowNanos - f.lastEndFrameNanos) / 1000);
        f.lastEndFrameNanos = nowNanos;
        Tracy.plotInt(PLOT_FRAMES_IN_FLIGHT, Device.framesInFlight());
        Tracy.plotInt(PLOT_MIP_GENS, f.mipGensThisFrame);
        Tracy.plotInt(PLOT_MIP_GEN_SUBMITS, f.mipGenSubmitsThisFrame);
        Tracy.plotInt(PLOT_UPLOAD_FLUSH_SUBMITS, f.uploadFlushSubmitsThisFrame);
        Tracy.plotInt(PLOT_BLIT_FALLBACKS, f.blitsThisFrame);
        Tracy.plotInt(PLOT_PRESENT_SKIPS, f.presentSkipsThisFrame);
        Tracy.plotInt(PLOT_EMPTY_FRAMES, f.emptyFramesThisFrame);
        Tracy.plotInt(PLOT_DROPPED_DRAWS, f.droppedDrawsThisFrame);
        Tracy.plotInt(PLOT_MID_FRAME_SUBMITS, f.midFrameSubmitsThisFrame);
    }
}
