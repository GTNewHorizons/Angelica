package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FormatMap;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureSamplerState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.util.MemoryAccess;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL30;

import org.lwjgl.sdl.SDL_GPUSamplerCreateInfo;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;


import static org.lwjgl.sdl.SDLGPU.*;

public final class SamplerBinder {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final LongOpenHashSet loggedFallbackSubs = new LongOpenHashSet();

    private final Device device;
    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;

    public SamplerBinder(Device device, ResourceManager resourceManager, ShaderManager shaderManager) {
        this.device = device;
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
    }

    public long getSamplerForTexture(int glTexId) {
        final TextureSamplerState ss = resourceManager.getTexSamplerState(glTexId);
        if (ss == null) return resourceManager.getOrCreateDefaultSampler();
        return getOrCreateSdlSampler(ss);
    }

    public long getSamplerForUnit(ContextState st, int glUnit, int glTexId) {
        if (glUnit >= 0 && glUnit < st.boundSamplerObjects.length && st.boundSamplerObjects[glUnit] != 0) {
            final TextureSamplerState ss = resourceManager.getSamplerObject(st.boundSamplerObjects[glUnit]);
            if (ss != null) return getOrCreateSdlSampler(ss);
        }
        if (glTexId != 0) return getSamplerForTexture(glTexId);
        return resourceManager.getOrCreateDefaultSampler();
    }

    public long getOrCreateSdlSampler(TextureSamplerState ss) {
        if (ss.sdlSampler != 0) return ss.sdlSampler;

        final SamplerCache.Key key = new SamplerCache.Key(
            ss.minFilter, ss.magFilter, ss.wrapS, ss.wrapT, ss.wrapR,
            ss.minLod, ss.maxLod, ss.lodBias,
            ss.maxAnisotropy, ss.compareMode, ss.compareFunc);

        final long handle = resourceManager.samplerCache().getOrCreate(key, this::createSamplerForKey);
        ss.sdlSampler = handle;
        return handle != 0 ? handle : resourceManager.getOrCreateDefaultSampler();
    }

    private long createSamplerForKey(SamplerCache.Key key) {
        try (var stack = MemoryStack.stackPush()) {
            final boolean enableCompare = key.compareMode() == GL30.GL_COMPARE_REF_TO_TEXTURE;
            final boolean wantsMipmap = FormatMap.isMipmapMinFilter(key.minFilter());
            final float effectiveMaxLod = wantsMipmap ? key.maxLod() : 0.0f;
            final SDL_GPUSamplerCreateInfo ci = SDL_GPUSamplerCreateInfo.calloc(stack)
                .min_filter(FormatMap.mapFilter(key.minFilter()))
                .mag_filter(FormatMap.mapFilter(key.magFilter()))
                .mipmap_mode(FormatMap.mapMipmapMode(key.minFilter()))
                .address_mode_u(FormatMap.mapAddressMode(key.wrapS()))
                .address_mode_v(FormatMap.mapAddressMode(key.wrapT()))
                .address_mode_w(FormatMap.mapAddressMode(key.wrapR()))
                .min_lod(key.minLod())
                .max_lod(effectiveMaxLod)
                .mip_lod_bias(key.lodBias())
                .enable_anisotropy(key.maxAnisotropy() > 1.0f)
                .max_anisotropy(key.maxAnisotropy())
                .enable_compare(enableCompare)
                .compare_op(enableCompare ? FormatMap.mapCompareOp(key.compareFunc()) : SDL_GPU_COMPAREOP_INVALID);

            return SDL_CreateGPUSampler(device.getDevice(), ci);
        }
    }

    public void bindSamplers(long renderPass, ContextState st, long bindCb) {
        if (st.boundProgram == 0) return;
        if (st.boundProgram == st.lastAppliedSamplerProgram && st.samplerBindGen == st.lastAppliedSamplerBindGen && bindCb == st.lastAppliedSamplerCb) {
            return;
        }
        final long fallbackTexture = resourceManager.getOrCreateFallbackTexture();
        if (fallbackTexture == 0) return;

        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) {
            bindFallbackForStage(renderPass, st, fallbackTexture, true);
            bindFallbackForStage(renderPass, st, fallbackTexture, false);
            st.lastAppliedSamplerProgram = st.boundProgram;
            st.lastAppliedSamplerBindGen = st.samplerBindGen;
            st.lastAppliedSamplerCb = bindCb;
            return;
        }

        bindStageSamplers(renderPass, st, prog, fallbackTexture, true);
        bindStageSamplers(renderPass, st, prog, fallbackTexture, false);
        st.lastAppliedSamplerProgram = st.boundProgram;
        st.lastAppliedSamplerBindGen = st.samplerBindGen;
        st.lastAppliedSamplerCb = bindCb;
    }

    private void bindStageSamplers(long renderPass, ContextState st, ShaderManager.ProgramObject prog, long fallbackTexture, boolean fragment) {
        final int raw = fragment ? prog.fragmentResources.numSamplers() : prog.vertexResources.numSamplers();
        if (raw > ContextState.MAX_SAMPLERS) {
            throw new IllegalStateException("program " + st.boundProgram + " has " + raw + " " + (fragment ? "fragment" : "vertex") + " samplers; MAX=" + ContextState.MAX_SAMPLERS);
        }
        if (raw <= 0) return;
        final SDL_GPUTextureSamplerBinding.Buffer bindings = (fragment ? st.fragSamplerBindings : st.vertSamplerBindings).position(0).limit(raw);
        final long bindingsBase = MemoryUtil.memAddress(bindings);
        final int[] samplerUnits = SamplerLookup.resolvedUnits(prog, fragment);
        final long[] lastTex = fragment ? st.lastFragSamplerTex : st.lastVertSamplerTex;
        final long[] lastSmp = fragment ? st.lastFragSamplerSmp : st.lastVertSamplerSmp;
        final int lastProg = fragment ? st.lastFragSamplerProgram : st.lastVertSamplerProgram;
        final boolean programChanged = st.boundProgram != lastProg;
        boolean anyChanged = programChanged;
        for (int i = 0; i < raw; i++) {
            final int glUnit = samplerUnits[i];
            final int glTexId = (glUnit >= 0 && glUnit < st.boundTextures.length) ? st.boundTextures[glUnit] : 0;
            long texHandle = (glTexId != 0) ? resourceManager.getTextureHandle(glTexId) : 0;
            if (texHandle == 0) {
                texHandle = fallbackTexture;
                if (loggedFallbackSubs.add(((long) st.boundProgram << 32) | (glUnit & 0xFFFFFFFFL))) {
                    LOG.warn("[SamplerBinder] program {} {} sampler slot {} (glUnit={} glTexId={}) has no SDL texture; substituting 2D fallback", st.boundProgram, fragment ? "fragment" : "vertex", i, glUnit, glTexId);
                }
            }
            final long sampler = getSamplerForUnit(st, glUnit, glTexId);
            if (programChanged || texHandle != lastTex[i] || sampler != lastSmp[i]) {
                anyChanged = true;
                final long elemAddr = bindingsBase + (long) i * SDL_GPUTextureSamplerBinding.SIZEOF;
                MemoryAccess.putAddress(elemAddr + SDL_GPUTextureSamplerBinding.TEXTURE, texHandle);
                MemoryAccess.putAddress(elemAddr + SDL_GPUTextureSamplerBinding.SAMPLER, sampler);
                lastTex[i] = texHandle;
                lastSmp[i] = sampler;
            }
        }
        if (anyChanged) {
            if (fragment) {
                SDL_BindGPUFragmentSamplers(renderPass, 0, bindings);
                st.lastFragSamplerProgram = st.boundProgram;
            } else {
                SDL_BindGPUVertexSamplers(renderPass, 0, bindings);
                st.lastVertSamplerProgram = st.boundProgram;
            }
        }
    }

    private void bindFallbackForStage(long renderPass, ContextState st, long fallbackTexture, boolean fragment) {
        final int count = ContextState.MAX_SAMPLERS;
        final SDL_GPUTextureSamplerBinding.Buffer bindings = (fragment ? st.fragSamplerBindings : st.vertSamplerBindings).position(0).limit(count);
        final long bindingsBase = MemoryUtil.memAddress(bindings);
        final long sampler = resourceManager.getOrCreateDefaultSampler();
        final long[] lastTex = fragment ? st.lastFragSamplerTex : st.lastVertSamplerTex;
        final long[] lastSmp = fragment ? st.lastFragSamplerSmp : st.lastVertSamplerSmp;
        for (int i = 0; i < count; i++) {
            final long elemAddr = bindingsBase + (long) i * SDL_GPUTextureSamplerBinding.SIZEOF;
            MemoryAccess.putAddress(elemAddr + SDL_GPUTextureSamplerBinding.TEXTURE, fallbackTexture);
            MemoryAccess.putAddress(elemAddr + SDL_GPUTextureSamplerBinding.SAMPLER, sampler);
            lastTex[i] = 0L;
            lastSmp[i] = 0L;
        }
        if (fragment) {
            SDL_BindGPUFragmentSamplers(renderPass, 0, bindings);
            st.lastFragSamplerProgram = 0;
        } else {
            SDL_BindGPUVertexSamplers(renderPass, 0, bindings);
            st.lastVertSamplerProgram = 0;
        }
    }
}
