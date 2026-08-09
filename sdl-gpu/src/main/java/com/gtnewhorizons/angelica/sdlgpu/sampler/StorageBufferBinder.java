package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.sdl.SDLGPU.nSDL_BindGPUFragmentStorageBuffers;
import static org.lwjgl.sdl.SDLGPU.nSDL_BindGPUVertexStorageBuffers;

public final class StorageBufferBinder {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;
    private final IntOpenHashSet nullHandleWarned = new IntOpenHashSet();

    public StorageBufferBinder(ResourceManager resourceManager, ShaderManager shaderManager) {
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
    }

    public void bindStorageBuffers(long renderPass, ContextState st, long bindCb) {
        if (st.boundProgram == 0) return;
        if (st.boundProgram == st.lastAppliedStorageBufProgram
            && st.ssboBindGen == st.lastAppliedStorageBufBindGen
            && bindCb == st.lastAppliedStorageBufCb) {
            return;
        }
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) {
            bindFallbackForStage(renderPass, st, true);
            bindFallbackForStage(renderPass, st, false);
            st.lastAppliedStorageBufProgram = st.boundProgram;
            st.lastAppliedStorageBufBindGen = st.ssboBindGen;
            st.lastAppliedStorageBufCb = bindCb;
            return;
        }

        bindStage(renderPass, st, prog, true);
        bindStage(renderPass, st, prog, false);

        st.lastAppliedStorageBufProgram = st.boundProgram;
        st.lastAppliedStorageBufBindGen = st.ssboBindGen;
        st.lastAppliedStorageBufCb = bindCb;
    }

    private void bindStage(long renderPass, ContextState st, ShaderManager.ProgramObject prog, boolean fragment) {
        final ShaderManager.GraphicsBindingMap map = fragment ? prog.fragmentGraphicsBindingMap : prog.vertexGraphicsBindingMap;
        final int[] slots = map.roSsboGlSlots();
        final int count = slots.length;
        if (count == 0) return;
        if (count > ContextState.MAX_STORAGE_BUFFERS_PER_STAGE) {
            throw new IllegalStateException("program " + st.boundProgram + " has " + count + " " + (fragment ? "fragment" : "vertex") + " SSBOs; MAX=" + ContextState.MAX_STORAGE_BUFFERS_PER_STAGE);
        }

        final PointerBuffer ptrs = (fragment ? st.fragStorageBufBindings : st.vertStorageBufBindings).position(0).limit(count);
        final long[] lastBuf = fragment ? st.lastFragStorageBuf : st.lastVertStorageBuf;
        final int lastProg = fragment ? st.lastFragStorageBufProgram : st.lastVertStorageBufProgram;
        boolean anyChanged = st.boundProgram != lastProg;

        final long fallback = resourceManager.getOrCreateFallbackStorageBuffer();
        for (int i = 0; i < count; i++) {
            final int glIndex = slots[i];
            final int glBufId = (glIndex >= 0 && glIndex < ContextState.MAX_INDEXED_BUFFERS) ? st.boundSsboByIndex[glIndex] : 0;
            long bufHandle = (glBufId != 0) ? resourceManager.getBufferHandle(glBufId) : 0;
            if (bufHandle == 0) {
                if (nullHandleWarned.add(st.boundProgram)) {
                    LOG.warn("StorageBufferBinder: program {} {} stage SSBO at glIndex={} has no bound buffer (glBufId={}); substituting fallback", st.boundProgram, fragment ? "fragment" : "vertex", glIndex, glBufId);
                }
                if (fallback == 0) return;
                bufHandle = fallback;
            }
            ptrs.put(i, bufHandle);
            if (bufHandle != lastBuf[i]) {
                anyChanged = true;
                lastBuf[i] = bufHandle;
            }
        }

        if (anyChanged) {
            if (Tracy.ENABLED) st.ssboBinds++;
            if (fragment) {
                nSDL_BindGPUFragmentStorageBuffers(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
                st.lastFragStorageBufProgram = st.boundProgram;
            } else {
                nSDL_BindGPUVertexStorageBuffers(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
                st.lastVertStorageBufProgram = st.boundProgram;
            }
        }
    }

    private void bindFallbackForStage(long renderPass, ContextState st, boolean fragment) {
        final int count = ContextState.MAX_STORAGE_BUFFERS_PER_STAGE;
        final long fallback = resourceManager.getOrCreateFallbackStorageBuffer();
        if (fallback == 0) return;
        final PointerBuffer ptrs = (fragment ? st.fragStorageBufBindings : st.vertStorageBufBindings).position(0).limit(count);
        final long[] lastBuf = fragment ? st.lastFragStorageBuf : st.lastVertStorageBuf;
        for (int i = 0; i < count; i++) {
            ptrs.put(i, fallback);
            lastBuf[i] = 0L;
        }
        if (Tracy.ENABLED) st.ssboBinds++;
        if (fragment) {
            nSDL_BindGPUFragmentStorageBuffers(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
            st.lastFragStorageBufProgram = 0;
        } else {
            nSDL_BindGPUVertexStorageBuffers(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
            st.lastVertStorageBufProgram = 0;
        }
    }
}
