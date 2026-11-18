package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.sdl.SDLGPU.nSDL_BindGPUFragmentStorageTextures;
import static org.lwjgl.sdl.SDLGPU.nSDL_BindGPUVertexStorageTextures;

public final class StorageTextureBinder {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;
    private final MovedSamplerCopies movedSamplerCopies;
    private final IntOpenHashSet nullHandleWarned = new IntOpenHashSet();

    public StorageTextureBinder(ResourceManager resourceManager, ShaderManager shaderManager, MovedSamplerCopies movedSamplerCopies) {
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
        this.movedSamplerCopies = movedSamplerCopies;
    }

    public void bindStorageTextures(long renderPass, ContextState st, long bindCb) {
        if (st.boundProgram == 0) return;
        if (st.boundProgram == st.lastAppliedStorageTexProgram && st.samplerBindGen == st.lastAppliedStorageTexBindGen && bindCb == st.lastAppliedStorageTexCb) {
            return;
        }
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) {
            bindFallbackForStage(renderPass, st, true);
            bindFallbackForStage(renderPass, st, false);
            st.lastAppliedStorageTexProgram = st.boundProgram;
            st.lastAppliedStorageTexBindGen = st.samplerBindGen;
            st.lastAppliedStorageTexCb = bindCb;
            return;
        }

        bindStage(renderPass, st, prog, true);
        bindStage(renderPass, st, prog, false);

        st.lastAppliedStorageTexProgram = st.boundProgram;
        st.lastAppliedStorageTexBindGen = st.samplerBindGen;
        st.lastAppliedStorageTexCb = bindCb;
    }

    private void bindStage(long renderPass, ContextState st, ShaderManager.ProgramObject prog, boolean fragment) {
        final List<String> imgNames = fragment ? prog.fragmentImageNames : prog.vertexImageNames;
        final int count = imgNames.size();
        if (count == 0) return;
        if (count > ContextState.MAX_IMAGE_UNITS) {
            throw new IllegalStateException("program " + st.boundProgram + " has " + count + " " + (fragment ? "fragment" : "vertex") + " storage images; MAX=" + ContextState.MAX_IMAGE_UNITS);
        }

        final PointerBuffer ptrs = (fragment ? st.fragStorageTexBindings : st.vertStorageTexBindings).position(0).limit(count);
        final long[] lastTex = fragment ? st.lastFragStorageTex : st.lastVertStorageTex;
        final int lastProg = fragment ? st.lastFragStorageTexProgram : st.lastVertStorageTexProgram;
        final boolean programChanged = st.boundProgram != lastProg;
        boolean anyChanged = programChanged;

        final int[] movedSamplerUnits = SamplerLookup.movedSamplerUnits(prog, fragment);
        final boolean[] movedSamplers = fragment ? prog.fragmentImageIsMovedSampler : prog.vertexImageIsMovedSampler;
        for (int i = 0; i < count; i++) {
            final String name = imgNames.get(i);
            final boolean movedSampler = movedSamplers[i];
            final int glUnit;
            final int glTexId;
            if (movedSampler) {
                glUnit = movedSamplerUnits[i];
                glTexId = st.boundTexture(glUnit);
            } else {
                glUnit = prog.imageTextureUnits.getInt(name);
                glTexId = (glUnit >= 0 && glUnit < ContextState.MAX_IMAGE_UNITS) ? st.boundStorageTextureByUnit[glUnit] : 0;
            }
            long texHandle = (glTexId != 0) ? resourceManager.getTextureHandle(glTexId) : 0;
            final boolean hasSource = texHandle != 0;
            if (movedSampler && hasSource) texHandle = movedSamplerCopies.textureFor(texHandle, glTexId);
            if (texHandle == 0) {
                final boolean unreadMovedSampler = movedSampler && glUnit < 0;
                if (!unreadMovedSampler && nullHandleWarned.add(st.boundProgram)) {
                    LOG.warn("StorageTextureBinder: program {} {} stage image '{}' {} (glUnit={} glTexId={}); substituting fallback", st.boundProgram, fragment ? "fragment" : "vertex", name,
                        hasSource ? "has no usable texture-slot copy" : "has no bound texture", glUnit, glTexId);
                }
                texHandle = movedSampler ? resourceManager.getOrCreateFallbackStorageTexture2D() : resourceManager.getOrCreateFallbackStorageTexture3D();
                if (texHandle == 0) return;
            }
            ptrs.put(i, texHandle);
            if (texHandle != lastTex[i]) {
                anyChanged = true;
                lastTex[i] = texHandle;
            }
        }

        if (anyChanged) {
            if (fragment) {
                nSDL_BindGPUFragmentStorageTextures(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
                st.lastFragStorageTexProgram = st.boundProgram;
            } else {
                nSDL_BindGPUVertexStorageTextures(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
                st.lastVertStorageTexProgram = st.boundProgram;
            }
        }
    }

    private void bindFallbackForStage(long renderPass, ContextState st, boolean fragment) {
        final int count = ContextState.MAX_IMAGE_UNITS;
        final long fallback = resourceManager.getOrCreateFallbackStorageTexture3D();
        if (fallback == 0) return;
        final PointerBuffer ptrs = (fragment ? st.fragStorageTexBindings : st.vertStorageTexBindings).position(0).limit(count);
        final long[] lastTex = fragment ? st.lastFragStorageTex : st.lastVertStorageTex;
        for (int i = 0; i < count; i++) {
            ptrs.put(i, fallback);
            lastTex[i] = 0L;
        }
        if (fragment) {
            nSDL_BindGPUFragmentStorageTextures(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
            st.lastFragStorageTexProgram = 0;
        } else {
            nSDL_BindGPUVertexStorageTextures(renderPass, 0, MemoryUtil.memAddress(ptrs), count);
            st.lastVertStorageTexProgram = 0;
        }
    }
}
