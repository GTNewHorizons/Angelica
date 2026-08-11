package com.gtnewhorizons.angelica.sdlgpu.compute;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.Hashing;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.sampler.SamplerBinder;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import org.lwjgl.PointerBuffer;
import org.lwjgl.sdl.SDL_GPUStorageBufferReadWriteBinding;
import org.lwjgl.sdl.SDL_GPUStorageTextureReadWriteBinding;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;

public final class ComputeBinder implements ComputeDispatchSink {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final Tracy.ZoneId Z_SDL_COMPUTE_PASS = Tracy.zoneId("sdlComputePass", Tracy.COLOR_TERRAIN);
    private final IntOpenHashSet loggedRoWithoutReadUsage = new IntOpenHashSet();
    private final LongOpenHashSet loggedMissingBindings = new LongOpenHashSet();

    private void warnRoWithoutReadUsage(ContextState st, int glId, int unit, int usage) {
        if (!loggedRoWithoutReadUsage.add(glId)) return;
        LOG.warn("[ComputeBinder] program {} binds texture {} at image unit {} as a readonly storage image, but its usage 0x{} lacks COMPUTE_STORAGE_READ; skipping the dispatch", st.boundProgram, glId, unit, Integer.toHexString(usage));
    }

    private void warnMissingBinding(ContextState st, int kindId, String kind, int slotOrUnit, boolean dropped) {
        final long key = ((long) st.boundProgram << 32) | ((long) kindId << 9) | ((long) (dropped ? 1 : 0) << 8) | (slotOrUnit & 0xFFL);
        if (!loggedMissingBindings.add(key)) return;
        LOG.warn("[ComputeBinder] program " + st.boundProgram + " has nothing bound at " + kind + " " + slotOrUnit + (dropped ? "; skipping the dispatch" : "; substituting a zeroed stand-in"), new Throwable("binding site"));
    }

    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;
    private final SamplerBinder samplerBinder;
    private boolean droppedDispatchWarned;

    public ComputeBinder(FrameManager frameManager, ResourceManager resourceManager, ShaderManager shaderManager, SamplerBinder samplerBinder) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
        this.samplerBinder = samplerBinder;
    }

    public void executeComputeDispatch(ContextState st, int gx, int gy, int gz, long indirectHandle, boolean indirect) {
        executeComputeDispatch(st, gx, gy, gz, indirectHandle, indirect, 0);
    }

    public void executeComputeDispatch(ContextState st, int gx, int gy, int gz, long indirectHandle, boolean indirect, int indirectOffset) {
        Tracy.beginZone(Z_SDL_COMPUTE_PASS);
        try {
            if (st.computeBatchRequested) {
                executeInBatchScope(st, gx, gy, gz, indirectHandle, indirect, indirectOffset);
                return;
            }
            final long pass = beginBatchedComputeDispatch(st, true);
            if (pass == 0) return;
            if (indirect) SDL_DispatchGPUComputeIndirect(pass, indirectHandle, indirectOffset);
            else SDL_DispatchGPUCompute(pass, gx, gy, gz);
            SDL_EndGPUComputePass(pass);
            frameManager.noteComputePassEnded();
        } finally {
            Tracy.endZone();
        }
    }

    private void executeInBatchScope(ContextState st, int gx, int gy, int gz, long indirectHandle, boolean indirect, int indirectOffset) {
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (st.computeBatchPass != 0 && canJoinBatch(st, prog)) {
            frameManager.noteComputeBatchJoin();
            final long pass = st.computeBatchPass;
            final ShaderManager.ComputeBindingMap map = prog.computeBindingMap;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                final boolean ok = bindComputeSamplers(st, prog, pass, map.samplerGlSlots(), map.samplerNames(), stack)
                    && bindRoStorageTextures(st, prog, pass, map, stack)
                    && bindRoStorageBuffers(st, pass, map.roSsboGlSlots(), stack);
                if (!ok) return;
            }
            pushComputeUboData(st, frameManager.getCommandBuffer(), prog, map.uboGlSlots());
            if (indirect) SDL_DispatchGPUComputeIndirect(pass, indirectHandle, indirectOffset);
            else SDL_DispatchGPUCompute(pass, gx, gy, gz);
            return;
        }

        endBatchPassIfOpen(st);
        final long pass = beginBatchedComputeDispatch(st, true);
        if (pass == 0) return;
        st.computeBatchPass = pass;
        st.computeBatchProgram = st.boundProgram;
        snapshotRwBindings(st, prog);
        if (indirect) SDL_DispatchGPUComputeIndirect(pass, indirectHandle, indirectOffset);
        else SDL_DispatchGPUCompute(pass, gx, gy, gz);
    }

    public void endBatchPassIfOpen(ContextState st) {
        if (st.computeBatchPass != 0) {
            SDL_EndGPUComputePass(st.computeBatchPass);
            frameManager.noteComputePassEnded();
            st.computeBatchPass = 0;
            st.computeBatchProgram = 0;
            st.computeBatchRwBufCount = -1;
            st.computeBatchRwTexCount = -1;
        }
    }

    private void snapshotRwBindings(ContextState st, ShaderManager.ProgramObject prog) {
        final ShaderManager.ComputeBindingMap map = prog.computeBindingMap;
        final int[] bufSlots = map.rwSsboGlSlots();
        for (int i = 0; i < bufSlots.length; i++) {
            st.computeBatchRwBufs[i] = resolveStorageBufHandle(st, bufSlots[i]);
        }
        st.computeBatchRwBufCount = bufSlots.length;
        final int[] texSlots = map.rwStorageTextureGlSlots();
        for (int i = 0; i < texSlots.length; i++) {
            final int unit = resolveUnit(prog.imageTextureUnits, map.rwStorageTextureNames(), i, texSlots[i]);
            final long real = resolveStorageTexHandle(st, unit);
            st.computeBatchRwTexs[i] = real != 0 ? real : standInStorageTex(st, map, i, unit, true);
            st.computeBatchRwTexLevels[i] = real != 0 ? st.boundStorageTextureLevel[unit] : 0;
        }
        st.computeBatchRwTexCount = texSlots.length;
    }

    private boolean canJoinBatch(ContextState st, ShaderManager.ProgramObject prog) {
        if (prog == null || !prog.linked || prog.sdlComputePipeline == 0) return false;
        if (st.boundProgram != st.computeBatchProgram) return false;
        final ShaderManager.ComputeBindingMap map = prog.computeBindingMap;
        final int[] bufSlots = map.rwSsboGlSlots();
        if (st.computeBatchRwBufCount != bufSlots.length) return false;
        for (int i = 0; i < bufSlots.length; i++) {
            if (resolveStorageBufHandle(st, bufSlots[i]) != st.computeBatchRwBufs[i]) return false;
        }
        final int[] texSlots = map.rwStorageTextureGlSlots();
        if (st.computeBatchRwTexCount != texSlots.length) return false;
        for (int i = 0; i < texSlots.length; i++) {
            final int unit = resolveUnit(prog.imageTextureUnits, map.rwStorageTextureNames(), i, texSlots[i]);
            final long real = resolveStorageTexHandle(st, unit);
            if ((real != 0 ? real : standInStorageTex(st, map, i, unit, true)) != st.computeBatchRwTexs[i]) return false;
            if ((real != 0 ? st.boundStorageTextureLevel[unit] : 0) != st.computeBatchRwTexLevels[i]) return false;
        }
        return true;
    }

    @Override
    public long beginBatchedComputeDispatch(ContextState st, boolean cycleRwBuffers) {
        final int programId = st.boundProgram;
        if (programId == 0) return 0;
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(programId);
        if (prog == null || !prog.linked || prog.sdlComputePipeline == 0) return 0;

        frameManager.endRenderPassIfActive();
        frameManager.endCopyPassIfActive();
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) {
            FrameManager.warnDroppedOutsideFrame(droppedDispatchWarned, "compute dispatch");
            droppedDispatchWarned = true;
            return 0;
        }

        final ShaderManager.ComputeBindingMap map = prog.computeBindingMap;
        final int[] samplerSlots = map.samplerGlSlots();
        final int[] rwTexSlots = map.rwStorageTextureGlSlots();
        final int[] roBufSlots = map.roSsboGlSlots();
        final int[] rwBufSlots = map.rwSsboGlSlots();
        final int[] uboSlots = map.uboGlSlots();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUStorageTextureReadWriteBinding.Buffer rwTex;
            if (rwTexSlots.length == 0) {
                rwTex = null;
            } else {
                rwTex = buildRwTextureBindings(st, prog, map, stack);
                if (rwTex == null) return 0;
            }
            final SDL_GPUStorageBufferReadWriteBinding.Buffer rwBuf;
            if (rwBufSlots.length == 0) {
                rwBuf = null;
            } else {
                rwBuf = buildRwBufferBindings(st, rwBufSlots, stack, cycleRwBuffers);
                if (rwBuf == null) return 0;
            }

            final long pass = SDL_BeginGPUComputePass(cb, rwTex, rwBuf);
            if (pass == 0) return 0;
            frameManager.noteComputePassBegun();

            final boolean ok = bindComputeSamplers(st, prog, pass, samplerSlots, map.samplerNames(), stack)
                && bindRoStorageTextures(st, prog, pass, map, stack)
                && bindRoStorageBuffers(st, pass, roBufSlots, stack);
            if (!ok) {
                SDL_EndGPUComputePass(pass);
                frameManager.noteComputePassEnded();
                return 0;
            }
            pushComputeUboData(st, cb, prog, uboSlots);

            SDL_BindGPUComputePipeline(pass, prog.sdlComputePipeline);
            return pass;
        }
    }

    @Override
    public void dispatchInBatch(long pass, int gx, int gy, int gz) {
        if (pass == 0) return;
        SDL_DispatchGPUCompute(pass, gx, gy, gz);
    }

    @Override
    public void rebindRoStorageBuffers(ContextState st, long pass) {
        if (pass == 0) return;
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            bindRoStorageBuffers(st, pass, prog.computeBindingMap.roSsboGlSlots(), stack);
        }
    }

    @Override
    public void endBatchedComputeDispatch(long pass) {
        if (pass == 0) return;
        SDL_EndGPUComputePass(pass);
        frameManager.noteComputePassEnded();
    }

    @Override
    public void pushPendingComputeUniforms(ContextState st) {
        final int programId = st.boundProgram;
        if (programId == 0) return;
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(programId);
        if (prog == null || !prog.linked) return;
        final long cb = frameManager.getCommandBuffer();
        if (cb == 0) return;
        final int[] uboSlots = prog.computeBindingMap.uboGlSlots();
        if (uboSlots.length == 0) return;
        pushComputeUboData(st, cb, prog, uboSlots);
    }

    private long resolveStorageTexHandle(ContextState st, int unit) {
        final int glId = st.boundStorageTextureByUnit[unit];
        return glId != 0 ? resourceManager.getTextureHandle(glId) : 0;
    }

    private long standInStorageTex(ContextState st, ShaderManager.ComputeBindingMap map, int i, int unit, boolean readWrite) {
        final int[] formats = readWrite ? map.rwStorageTextureFormats() : map.roStorageTextureFormats();
        final int[] targets = readWrite ? map.rwStorageTextureTargets() : map.roStorageTextureTargets();
        if (i >= formats.length || i >= targets.length) return 0;

        final int glFormat = formats[i] != 0 ? formats[i] : st.boundStorageTextureFormat[unit];
        if (glFormat == 0) return 0;
        return resourceManager.getOrCreateComputeStandInTexture(glFormat, targets[i], readWrite);
    }

    private static int resolveUnit(Object2IntOpenHashMap<String> unitsByName, String[] names, int i, int fallback) {
        if (names != null && i < names.length) {
            final String name = names[i];
            if (name != null && unitsByName.containsKey(name)) return unitsByName.getInt(name);
        }
        return fallback;
    }

    private long resolveStorageBufHandle(ContextState st, int slot) {
        final int glId = st.boundSsboByIndex[slot];
        return glId != 0 ? resourceManager.getBufferHandle(glId) : 0;
    }

    private SDL_GPUStorageTextureReadWriteBinding.Buffer buildRwTextureBindings(ContextState st, ShaderManager.ProgramObject prog, ShaderManager.ComputeBindingMap map, MemoryStack stack) {
        final int[] units = map.rwStorageTextureGlSlots();
        final SDL_GPUStorageTextureReadWriteBinding.Buffer buf = SDL_GPUStorageTextureReadWriteBinding.calloc(units.length, stack);
        for (int i = 0; i < units.length; i++) {
            final int unit = resolveUnit(prog.imageTextureUnits, map.rwStorageTextureNames(), i, units[i]);
            long handle = resolveStorageTexHandle(st, unit);
            int level = st.boundStorageTextureLevel[unit];
            if (handle == 0) {
                handle = standInStorageTex(st, map, i, unit, true);
                level = 0;
                if (handle == 0) {
                    warnMissingBinding(st, 0, "RW storage image unit", unit, true);
                    return null;
                }
                warnMissingBinding(st, 0, "RW storage image unit", unit, false);
            }
            buf.get(i).texture(handle).mip_level(level).layer(0).cycle(false);
        }
        buf.position(0);
        return buf;
    }

    private SDL_GPUStorageBufferReadWriteBinding.Buffer buildRwBufferBindings(ContextState st, int[] slots, MemoryStack stack, boolean cycle) {
        final SDL_GPUStorageBufferReadWriteBinding.Buffer buf = SDL_GPUStorageBufferReadWriteBinding.calloc(slots.length, stack);
        for (int i = 0; i < slots.length; i++) {
            final long handle = resolveStorageBufHandle(st, slots[i]);

            if (handle == 0) { warnMissingBinding(st, 1, "RW storage buffer slot", slots[i], true); return null; }
            buf.get(i).buffer(handle).cycle(cycle);
        }
        buf.position(0);
        return buf;
    }

    private boolean bindComputeSamplers(ContextState st, ShaderManager.ProgramObject prog, long pass, int[] units, String[] names, MemoryStack stack) {
        if (units.length == 0) return true;
        final SDL_GPUTextureSamplerBinding.Buffer buf = SDL_GPUTextureSamplerBinding.calloc(units.length, stack);
        for (int i = 0; i < units.length; i++) {
            final int unit = resolveUnit(prog.samplerTextureUnits, names, i, units[i]);
            final int texGlId = st.boundTextures[unit];
            long texHandle = texGlId != 0 ? resourceManager.getTextureHandle(texGlId) : 0;
            if (texHandle == 0) {
                texHandle = resourceManager.getOrCreateFallbackTexture();
                if (texHandle == 0) { warnMissingBinding(st, 2, "sampler texture unit", unit, true); return false; }
                warnMissingBinding(st, 2, "sampler texture unit", unit, false);
            }
            long sampHandle = samplerBinder.getSamplerForUnit(st, unit, texGlId);
            if (sampHandle == 0) {
                sampHandle = resourceManager.getOrCreateDefaultSampler();
                if (sampHandle == 0) { warnMissingBinding(st, 3, "sampler unit", unit, true); return false; }
                warnMissingBinding(st, 3, "sampler unit", unit, false);
            }
            buf.get(i).texture(texHandle).sampler(sampHandle);
        }
        buf.position(0);
        SDL_BindGPUComputeSamplers(pass, 0, buf);
        return true;
    }

    private boolean bindRoStorageTextures(ContextState st, ShaderManager.ProgramObject prog, long pass, ShaderManager.ComputeBindingMap map, MemoryStack stack) {
        final int[] units = map.roStorageTextureGlSlots();
        if (units.length == 0) return true;
        final PointerBuffer ptrs = stack.mallocPointer(units.length);
        for (int i = 0; i < units.length; i++) {
            final int unit = resolveUnit(prog.imageTextureUnits, map.roStorageTextureNames(), i, units[i]);
            long handle = resolveStorageTexHandle(st, unit);
            if (handle == 0) {
                handle = standInStorageTex(st, map, i, unit, false);
                if (handle == 0) { warnMissingBinding(st, 4, "RO storage image unit", unit, true); return false; }
                warnMissingBinding(st, 4, "RO storage image unit", unit, false);
                ptrs.put(i, handle);
                continue;
            }
            final int glId = st.boundStorageTextureByUnit[unit];
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(glId);
            if (meta != null && (meta.usage() & SDL_GPU_TEXTUREUSAGE_COMPUTE_STORAGE_READ) == 0) {
                warnRoWithoutReadUsage(st, glId, unit, meta.usage());
                return false;
            }
            ptrs.put(i, handle);
        }
        SDL_BindGPUComputeStorageTextures(pass, 0, ptrs);
        return true;
    }

    private boolean bindRoStorageBuffers(ContextState st, long pass, int[] slots, MemoryStack stack) {
        if (slots.length == 0) return true;
        final PointerBuffer ptrs = stack.mallocPointer(slots.length);
        for (int i = 0; i < slots.length; i++) {
            long handle = resolveStorageBufHandle(st, slots[i]);
            if (handle == 0) {
                handle = resourceManager.getOrCreateFallbackStorageBuffer();
                if (handle == 0) { warnMissingBinding(st, 5, "RO storage buffer slot", slots[i], true); return false; }
                warnMissingBinding(st, 5, "RO storage buffer slot", slots[i], false);
            }
            ptrs.put(i, handle);
        }
        SDL_BindGPUComputeStorageBuffers(pass, 0, ptrs);
        return true;
    }

    private void pushComputeUboData(ContextState st, long cb, ShaderManager.ProgramObject prog, int[] uboSlots) {
        if (prog.lastComputeUboHash == null || prog.lastComputeUboHash.length != uboSlots.length) {
            prog.lastComputeUboHash = new long[uboSlots.length];
            prog.lastComputeCb = 0;
            prog.lastComputeFrame = 0;
        }
        final long curFrame = frameManager.getFrameNumber();
        final boolean cacheValid = cb == prog.lastComputeCb && curFrame == prog.lastComputeFrame;
        final long[] cache = prog.lastComputeUboHash;
        if (!cacheValid) {
            for (int i = 0; i < cache.length; i++) cache[i] = 0L;
            prog.lastComputeCb = cb;
            prog.lastComputeFrame = curFrame;
        }
        final ShaderManager.ComputeBindingMap map = prog.computeBindingMap;
        final boolean[] isDefault = map.uboIsDefaultBlock();
        final int[] declaredSizes = map.uboSizes();
        final UniformStaging us = st.uniformStaging(prog);
        for (int i = 0; i < uboSlots.length; i++) {
            final ByteBuffer view;
            if (i < isDefault.length && isDefault[i]) {
                if (us.fsUniformBuf == null || prog.fragmentUboSize <= 0) continue;
                view = us.fsUniformBuf;
                view.position(0).limit(prog.fragmentUboSize);
            } else {
                final int slot = uboSlots[i];
                final int bufGlId = st.boundUboByIndex[slot];
                if (bufGlId == 0) continue;
                final ByteBuffer shadow = resourceManager.getUboShadow(bufGlId);
                if (shadow == null) continue;
                final int offset = st.uboRangeOffset[slot];
                if (offset < 0 || offset >= shadow.capacity()) continue;
                int size = (i < declaredSizes.length && declaredSizes[i] > 0) ? declaredSizes[i] : shadow.capacity() - offset;
                final int rangeSize = st.uboRangeSize[slot];
                if (rangeSize > 0) size = Math.min(size, rangeSize);
                size = Math.min(size, shadow.capacity() - offset);
                if (size <= 0) continue;
                view = frameManager.getUboPushView(bufGlId, shadow);
                view.position(offset).limit(offset + size);
            }
            final int pos = view.position();
            final int len = view.remaining();
            final long hash = Hashing.hashBytes(view, pos, len);
            if (cache[i] == hash && hash != 0L) {
                continue;
            }
            SDL_PushGPUComputeUniformData(cb, i, view);
            cache[i] = hash;
        }
    }
}
