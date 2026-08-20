package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineCreateInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineTargetInfo;
import org.lwjgl.sdl.SDL_GPUMultisampleState;
import org.lwjgl.sdl.SDL_GPURasterizerState;
import org.lwjgl.sdl.SDL_GPUVertexAttribute;
import org.lwjgl.sdl.SDL_GPUVertexBufferDescription;
import org.lwjgl.sdl.SDL_GPUVertexInputState;
import org.lwjgl.sdl.SDLError;
import com.gtnewhorizons.angelica.glsm.GLTypes;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UscaledRetype;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntToLongFunction;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.*;

public final class PipelineCache {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final Tracy.ZoneId Z_SDL_PIPELINE_CREATE = Tracy.zoneId("sdlPipelineCreate", Tracy.COLOR_CLIENT);

    private static final long OUTPUT_SEED = 0x9E3779B97F4A7C15L;
    private static final long SHADER_SEED = 0xBB67AE8584CAA73BL;
    private static final long INPUT_SEED  = 0x3C6EF372FE94F82BL;

    public int keyRecomputes;
    public int fastPathHits;

    private long lastKey;
    private long lastPipeline;

    private boolean outputDirty = true;
    private boolean shaderDirty = true;
    private boolean inputDirty  = true;

    private long cachedOutputHash;
    private long cachedShaderHash;
    private long cachedInputHash;

    public int primitiveType = SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;

    public final boolean[] blendEnabledPerAttachment = new boolean[ContextState.MAX_COLOR_ATTACHMENTS];
    public int srcColorFactor = SDL_GPU_BLENDFACTOR_ONE;
    public int dstColorFactor = SDL_GPU_BLENDFACTOR_ZERO;
    public int srcAlphaFactor = SDL_GPU_BLENDFACTOR_ONE;
    public int dstAlphaFactor = SDL_GPU_BLENDFACTOR_ZERO;
    public int colorBlendOp = SDL_GPU_BLENDOP_ADD;
    public int alphaBlendOp = SDL_GPU_BLENDOP_ADD;

    public boolean depthTestEnabled;
    public boolean depthWriteEnabled = true;
    public int depthCompareOp = SDL_GPU_COMPAREOP_LESS;

    public boolean stencilTestEnabled;
    public boolean effectiveStencilTestEnabled() {
        return stencilTestEnabled && hasDepthTarget && PixelOps.isDepthStencilFormat(depthTargetFormat);
    }
    public int stencilFrontCompareOp = SDL_GPU_COMPAREOP_ALWAYS;
    public int stencilFrontFailOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilFrontDepthFailOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilFrontPassOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilBackCompareOp = SDL_GPU_COMPAREOP_ALWAYS;
    public int stencilBackFailOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilBackDepthFailOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilBackPassOp = SDL_GPU_STENCILOP_KEEP;
    public int stencilCompareMask = 0xFF;
    public int stencilWriteMask = 0xFF;

    private static final LongOpenHashSet loggedStencilMaskLost = new LongOpenHashSet();

    private void warnOnceOnStencilMaskLost() {
        if (!stencilTestEnabled || effectiveStencilTestEnabled()) return;
        if (stencilFrontCompareOp == SDL_GPU_COMPAREOP_ALWAYS && stencilBackCompareOp == SDL_GPU_COMPAREOP_ALWAYS && stencilFrontPassOp == SDL_GPU_STENCILOP_KEEP && stencilBackPassOp == SDL_GPU_STENCILOP_KEEP)
            return;
        if (!loggedStencilMaskLost.add(Hashing.packHiLo(programId, debugFboId))) return;
        LOG.warn("Stencil mask dropped: bound target has no stencil aspect. fbo={} depthFormat={} program={} frontCmp={} frontPass={} writeMask=0x{}",
            debugFboId, textureFormatName(depthTargetFormat), programId, stencilFrontCompareOp, stencilFrontPassOp, Integer.toHexString(stencilWriteMask));
    }

    public boolean cullEnabled = false;
    public int cullFaceMode = SDL_GPU_CULLMODE_BACK;
    public int frontFace = SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE;
    public boolean cullAll = false;

    public int getEffectiveCullMode() {
        return cullEnabled ? cullFaceMode : SDL_GPU_CULLMODE_NONE;
    }
    public int fillMode = SDL_GPU_FILLMODE_FILL;
    public float depthBiasConstant;
    public float depthBiasSlopeFactor;
    public float depthBiasClamp;

    public int colorWriteMask = SDL_GPU_COLORCOMPONENT_R | SDL_GPU_COLORCOMPONENT_G | SDL_GPU_COLORCOMPONENT_B | SDL_GPU_COLORCOMPONENT_A;

    public long vertexShader;
    public long fragmentShader;
    public int programId;
    public int maxFragOutputLocation = -1;
    private int debugFboId;
    private int debugFboAttachments;

    public void setFboDebug(int fboId, int colorAttachmentCount) {
        this.debugFboId = fboId;
        this.debugFboAttachments = colorAttachmentCount;
    }

    private static volatile int[] swapchainFormats;

    public static void setSwapchainFormats(int[] formats) {
        swapchainFormats = formats;
    }

    private int[] colorTargetFormats;
    public int depthTargetFormat;
    public boolean hasDepthTarget;

    public void setColorTargetFormats(int[] formats) {
        this.colorTargetFormats = formats != null && formats.length > 0 ? formats : null;
    }

    private int[] colorTargetFormats() {
        final int[] explicit = colorTargetFormats;
        if (explicit != null) return explicit;
        final int[] swapchain = swapchainFormats;
        if (swapchain == null) throw new IllegalStateException("pipeline built before the swapchain format was known");
        return swapchain;
    }

    private int[] drawBuffers;

    public void setDrawBuffers(int[] drawBuffers) {
        this.drawBuffers = drawBuffers;
    }

    public boolean setDepthTargetFormat(int sdlFormat) {
        if (depthTargetFormat == sdlFormat && hasDepthTarget == (sdlFormat != 0)) return false;
        hasDepthTarget = sdlFormat != 0;
        depthTargetFormat = sdlFormat;
        markOutputDirty();
        return true;
    }

    private int attachmentForSlot(int slot) {
        final int[] db = drawBuffers;
        if (db == null || slot < 0 || slot >= db.length) return 0;
        final int idx = db[slot];
        return (idx >= 0 && idx < ContextState.MAX_COLOR_ATTACHMENTS) ? idx : 0;
    }

    private boolean blendEnabledForSlot(int slot) {
        return blendEnabledPerAttachment[attachmentForSlot(slot)];
    }

    public int maxAttribs;

    private static final int[] EMPTY_INT = new int[0];
    private static final String[] EMPTY_STR = new String[0];

    public int shaderInputMask;
    public int[] shaderInputVecSize  = EMPTY_INT;
    public int[] shaderInputBaseType = EMPTY_INT;
    public String[] shaderInputName = EMPTY_STR;

    public PipelineCache() {
        this.maxAttribs = ContextState.MAX_VERTEX_ATTRIBS;
    }

    public void markInputDirty()  { inputDirty  = true; lastKey = 0L; }

    public boolean markInputDirtyIfLivenessChanged(PipelineStore store, int oldBuffer, int newBuffer) {
        final IntToLongFunction resolver = store.bufferHandleResolver();
        if (resolver == null) return false;
        if ((resolver.applyAsLong(oldBuffer) == 0L) == (resolver.applyAsLong(newBuffer) == 0L)) return false;
        markInputDirty();
        return true;
    }

    public void markOutputDirty() { outputDirty = true; lastKey = 0L; }
    public void markShaderDirty() { shaderDirty = true; lastKey = 0L; }

    public void invalidateShaderHandles() {
        vertexShader = 0;
        fragmentShader = 0;
        programId = 0;
        markShaderDirty();
    }

    private boolean zeroShaderWarned;

    private boolean buildFlipFrontFace;
    private int effectiveFrontFace() {
        if (!buildFlipFrontFace) return frontFace;
        return (frontFace == SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE)
            ? SDL_GPU_FRONTFACE_CLOCKWISE : SDL_GPU_FRONTFACE_COUNTER_CLOCKWISE;
    }

    public long getOrCreatePipeline(PipelineStore store, ContextState cs, boolean fboFlipFrontFace) {
        if (this.buildFlipFrontFace != fboFlipFrontFace) {
            this.buildFlipFrontFace = fboFlipFrontFace;
            this.outputDirty = true;
            this.lastKey = 0L;
        }
        return getOrCreatePipeline(store, cs);
    }

    public long getOrCreatePipeline(PipelineStore store, ContextState cs) {
        if (vertexShader == 0 || fragmentShader == 0) {
            if (!zeroShaderWarned) {
                zeroShaderWarned = true;
                LOG.warn("PipelineCache.getOrCreatePipeline: vs={} fs={} - one or both shaders missing, returning 0", vertexShader, fragmentShader);
            }
            return 0;
        }
        final long lk = lastKey;
        if (lk != 0L && !outputDirty && !shaderDirty && !inputDirty) {
            final long lp = lastPipeline;
            if (lp != 0L) {
                if (Tracy.ENABLED) fastPathHits++;
                return lp;
            }
        }
        if (Tracy.ENABLED) keyRecomputes++;
        final long key = currentKey(store, cs);
        if (key == lastKey && lastPipeline != 0L) return lastPipeline;
        builder.store = store;
        builder.cs = cs;
        final long hit = store.getOrBuild(key, builder);
        if (hit != 0L) {
            lastPipeline = hit;
            lastKey = key;
        }
        return hit;
    }

    private final Builder builder = new Builder();

    private final class Builder implements LongSupplier {
        PipelineStore store;
        ContextState cs;

        @Override
        public long getAsLong() {
            return createPipeline(store, cs);
        }
    }

    private long currentKey(PipelineStore store, ContextState cs) {
        if (outputDirty) { cachedOutputHash = computeOutputHash();         outputDirty = false; }
        if (shaderDirty) { cachedShaderHash = computeShaderHash();         shaderDirty = false; }
        if (inputDirty)  { cachedInputHash  = computeInputHash(store, cs); inputDirty  = false; }
        return cachedOutputHash ^ cachedShaderHash ^ cachedInputHash;
    }

    public long computeKey(PipelineStore store, ContextState cs) {
        return computeOutputHash() ^ computeShaderHash() ^ computeInputHash(store, cs);
    }

    private long computeOutputHash() {
        final boolean depthOn = depthTestEnabled && hasDepthTarget;
        final boolean depthWriteOn = depthOn && depthWriteEnabled;
        final boolean stencilOn = effectiveStencilTestEnabled();
        final int ctfLen = colorTargetFormats().length;
        int blendBits = 0;
        for (int i = 0; i < ctfLen && i < 32; i++) {
            if (blendEnabledForSlot(i)) blendBits |= 1 << i;
        }
        final long bits =
              ((depthOn             ? 1L : 0L) <<  1)
            | ((depthWriteOn        ? 1L : 0L) <<  2)
            | ((stencilOn           ? 1L : 0L) <<  3)
            | ((hasDepthTarget      ? 1L : 0L) <<  4)
            | (((long) blendBits & 0xFFFFFFFFL) << 32);
        long h = OUTPUT_SEED;
        h = Hashing.fmix64(h, bits);
        h = Hashing.fmix64(h, Hashing.packHiLo(primitiveType, srcColorFactor));
        h = Hashing.fmix64(h, Hashing.packHiLo(dstColorFactor, srcAlphaFactor));
        h = Hashing.fmix64(h, Hashing.packHiLo(dstAlphaFactor, colorBlendOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(alphaBlendOp, depthCompareOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(stencilFrontCompareOp, stencilFrontFailOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(stencilFrontDepthFailOp, stencilFrontPassOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(stencilBackCompareOp, stencilBackFailOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(stencilBackDepthFailOp, stencilBackPassOp));
        h = Hashing.fmix64(h, Hashing.packHiLo(stencilCompareMask, stencilWriteMask));
        h = Hashing.fmix64(h, Hashing.packHiLo(getEffectiveCullMode(), effectiveFrontFace()));
        h = Hashing.fmix64(h, Hashing.packHiLo(fillMode, colorWriteMask));
        h = Hashing.fmix64(h, Hashing.packHiLo(Float.floatToRawIntBits(depthBiasConstant), Float.floatToRawIntBits(depthBiasSlopeFactor)));
        h = Hashing.fmix64(h, Float.floatToRawIntBits(depthBiasClamp));
        h = Hashing.fmix64(h, Hashing.packHiLo(ctfLen, hasDepthTarget ? depthTargetFormat : 0));
        for (int i = 0; i < ctfLen; i++) {
            h = Hashing.fmix64(h, colorTargetFormats()[i]);
        }
        return h;
    }

    private long computeShaderHash() {
        long h = SHADER_SEED;
        h = Hashing.fmix64(h, vertexShader);
        h = Hashing.fmix64(h, fragmentShader);
        h = Hashing.fmix64(h, programId);
        return h;
    }

    private long computeInputHash(PipelineStore store, ContextState cs) {
        long h = INPUT_SEED;
        h = Hashing.fmix64(h, Hashing.packHiLo(shaderInputMask, maxAttribs));
        if (cs == null) return h;
        final ContextState.VAOState vao = cs.currentVao;
        final int sibtLen = shaderInputBaseType.length;
        final int sivsLen = shaderInputVecSize.length;

        for (int i = 0; i < maxAttribs; i++) {
            if ((shaderInputMask & (1 << i)) == 0) continue;
            if (attribLive(store, cs, vao, i)) {
                final int b = vao.attribBinding[i];
                final long w0 =
                      ((long) i & 0xFF)
                    | (((long) vao.attribSize[i] & 0xFF) << 8)
                    | (((long) vao.attribType[i] & 0xFFFFL) << 16)
                    | ((vao.attribNormalized[i] ? 1L : 0L) << 32)
                    | ((vao.attribIsInteger[i] ? 1L : 0L) << 33)
                    | (((long) b & 0xFF) << 40)
                    | (((long) vao.attribStride[i] & 0xFFFFL) << 48);
                final long w1 =
                      ((long) vao.attribRelativeOffset[i] & 0xFFFFL)
                    | (((long) vao.bindingStride[b] & 0xFFFFL) << 16)
                    | (((long) vao.bindingDivisor[b] & 0xFFFFL) << 32)
                    | ((i < sibtLen ? (long) shaderInputBaseType[i] & 0xFFFFL : 0L) << 48);
                h = Hashing.fmix64(h, w0);
                h = Hashing.fmix64(h, w1);
            } else if (i < sivsLen) {
                h = Hashing.fmix64(h, Hashing.packHiLo(i, shaderInputVecSize[i]));
            }
        }
        return h;
    }

    private static final LongOpenHashSet loggedUnconsumedFragOutput = new LongOpenHashSet();

    private void warnOnceOnUnconsumedFragOutput(int numColorTargets) {
        if (maxFragOutputLocation < numColorTargets) return;
        if (!loggedUnconsumedFragOutput.add(Hashing.packHiLo(programId, numColorTargets))) return;
        LOG.warn("[PipelineCache] program {} declares fragment output location {} but framebuffer {} ({} color attachment(s)) has {} draw buffer(s) {}; writes past location {} are discarded", programId, maxFragOutputLocation, debugFboId, debugFboAttachments, numColorTargets, Arrays.toString(drawBuffers), numColorTargets - 1);
    }

    private long createPipeline(PipelineStore store, ContextState cs) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_SDL_PIPELINE_CREATE);
        try {
            return createPipelineInner(store, cs);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private long createPipelineInner(PipelineStore store, ContextState cs) {
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }

        try (var stack = stackPush()) {
            final int numColorTargets = colorTargetFormats().length;
            warnOnceOnUnconsumedFragOutput(numColorTargets);
            warnOnceOnStencilMaskLost();
            final SDL_GPUColorTargetDescription.Buffer colorDesc = SDL_GPUColorTargetDescription.calloc(numColorTargets, stack);
            for (int i = 0; i < numColorTargets; i++) {
                final var desc = colorDesc.get(i);
                desc.format(colorTargetFormats()[i]);
                desc.blend_state()
                    .enable_blend(blendEnabledForSlot(i))
                    .src_color_blendfactor(srcColorFactor)
                    .dst_color_blendfactor(dstColorFactor)
                    .color_blend_op(colorBlendOp)
                    .src_alpha_blendfactor(srcAlphaFactor)
                    .dst_alpha_blendfactor(dstAlphaFactor)
                    .alpha_blend_op(alphaBlendOp)
                    .color_write_mask((byte) colorWriteMask)
                    .enable_color_write_mask(true);
            }

            final boolean depthOn = depthTestEnabled && hasDepthTarget;
            final boolean stencilOn = effectiveStencilTestEnabled();
            final SDL_GPUDepthStencilState depthStencil = SDL_GPUDepthStencilState.calloc(stack)
                .enable_depth_test(depthOn)
                .enable_depth_write(depthOn && depthWriteEnabled)
                .compare_op(depthCompareOp)
                .enable_stencil_test(stencilOn)
                .compare_mask((byte) stencilCompareMask)
                .write_mask((byte) stencilWriteMask);
            if (stencilOn) {
                depthStencil.front_stencil_state()
                    .fail_op(stencilFrontFailOp)
                    .pass_op(stencilFrontPassOp)
                    .depth_fail_op(stencilFrontDepthFailOp)
                    .compare_op(stencilFrontCompareOp);
                depthStencil.back_stencil_state()
                    .fail_op(stencilBackFailOp)
                    .pass_op(stencilBackPassOp)
                    .depth_fail_op(stencilBackDepthFailOp)
                    .compare_op(stencilBackCompareOp);
            }

            final SDL_GPURasterizerState rasterizer = SDL_GPURasterizerState.calloc(stack)
                .cull_mode(getEffectiveCullMode())
                .front_face(effectiveFrontFace())
                .fill_mode(fillMode)
                .depth_bias_constant_factor(depthBiasConstant)
                .depth_bias_slope_factor(depthBiasSlopeFactor)
                .depth_bias_clamp(depthBiasClamp);

            final SDL_GPUMultisampleState multisample = SDL_GPUMultisampleState.calloc(stack)
                .sample_count(SDL_GPU_SAMPLECOUNT_1);

            final SDL_GPUGraphicsPipelineTargetInfo targetInfo = SDL_GPUGraphicsPipelineTargetInfo.calloc(stack)
                .num_color_targets(numColorTargets)
                .color_target_descriptions(colorDesc);
            if (hasDepthTarget) {
                targetInfo.depth_stencil_format(depthTargetFormat)
                    .has_depth_stencil_target(true);
            }

            final SDL_GPUVertexInputState vertexInput = SDL_GPUVertexInputState.calloc(stack);
            final VertexInputResult vi = buildVertexInput(store, cs, stack);
            if (vi == null) return PipelineStore.BAD_PIPELINE_SENTINEL;
            if (vi.numAttributes() > 0) {
                vertexInput
                    .num_vertex_buffers(vi.numBuffers())
                    .vertex_buffer_descriptions(vi.bindings())
                    .num_vertex_attributes(vi.numAttributes())
                    .vertex_attributes(vi.attrs());
            }

            final SDL_GPUGraphicsPipelineCreateInfo ci = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
                .vertex_shader(vi.vertexShaderOverride() != 0L ? vi.vertexShaderOverride() : vertexShader)
                .fragment_shader(fragmentShader)
                .primitive_type(primitiveType)
                .rasterizer_state(rasterizer)
                .multisample_state(multisample)
                .depth_stencil_state(depthStencil)
                .target_info(targetInfo)
                .vertex_input_state(vertexInput);

            final long pipeline = SDL_CreateGPUGraphicsPipeline(store.device().getDevice(), ci);
            if (pipeline == 0) {
                final int diagInputMask = computeInputMask();
                logPipelineCreateFailure(cs, diagInputMask, Integer.bitCount(diagInputMask), depthOn, stencilOn);
                return PipelineStore.BAD_PIPELINE_SENTINEL;
            }
            LOG.debug("Pipeline created: {}", pipeline);
            return pipeline;
        }
    }

    private void logPipelineCreateFailure(ContextState cs, int inputMask, int totalInputs, boolean depthOn, boolean stencilOn) {
        final String sdlErr = SDLError.SDL_GetError();
        final int boundProgram = cs != null ? cs.boundProgram : -1;
        final StringBuilder sb = new StringBuilder(2048);
        sb.append("Failed to create graphics pipeline (D3D12/Vulkan/Metal PSO rejected):\n");
        sb.append("  SDL error: ").append(sdlErr).append('\n');
        sb.append("  Program: boundProgram=").append(boundProgram)
          .append(" vs=0x").append(Long.toHexString(vertexShader))
          .append(" fs=0x").append(Long.toHexString(fragmentShader)).append('\n');
        sb.append("  Primitive: type=").append(primitiveType).append('\n');
        sb.append("  Targets: numColor=").append(colorTargetFormats().length)
          .append(" hasDepth=").append(hasDepthTarget)
          .append(" depthFormat=").append(depthTargetFormat)
          .append(" (").append(textureFormatName(depthTargetFormat)).append(")\n");
        for (int i = 0; i < colorTargetFormats().length; i++) {
            sb.append("    color[").append(i).append("]: format=").append(colorTargetFormats()[i])
              .append(" (").append(textureFormatName(colorTargetFormats()[i])).append(")\n");
        }
        sb.append("  Sample count: 1\n");
        sb.append("  Depth/stencil: depthOn=").append(depthOn)
          .append(" rawDepthTest=").append(depthTestEnabled)
          .append(" rawDepthWrite=").append(depthWriteEnabled)
          .append(" compareOp=").append(depthCompareOp)
          .append(" stencilOn=").append(stencilOn).append('\n');
        if (stencilOn) {
            sb.append("  Stencil: cmpMask=0x").append(Integer.toHexString(stencilCompareMask))
              .append(" wrMask=0x").append(Integer.toHexString(stencilWriteMask))
              .append(" frontCmp=").append(stencilFrontCompareOp)
              .append(" backCmp=").append(stencilBackCompareOp).append('\n');
        }
        sb.append("  Blend: enabled=").append(blendEnabledForSlot(0))
          .append(" srcColor=").append(srcColorFactor).append(" dstColor=").append(dstColorFactor).append(" colorOp=").append(colorBlendOp)
          .append(" srcAlpha=").append(srcAlphaFactor).append(" dstAlpha=").append(dstAlphaFactor).append(" alphaOp=").append(alphaBlendOp)
          .append(" mask=0x").append(Integer.toHexString(colorWriteMask)).append('\n');
        sb.append("  Rasterizer: cull=").append(getEffectiveCullMode())
          .append(" front=").append(effectiveFrontFace())
          .append(" fill=").append(fillMode)
          .append(" biasConst=").append(depthBiasConstant)
          .append(" biasSlope=").append(depthBiasSlopeFactor)
          .append(" biasClamp=").append(depthBiasClamp).append('\n');
        sb.append("  ShaderInputs: mask=0x").append(Integer.toHexString(shaderInputMask))
          .append(" baseTypes=").append(intArrayToString(shaderInputBaseType))
          .append(" vecSizes=").append(intArrayToString(shaderInputVecSize)).append('\n');
        sb.append("  VertexInput: totalInputs=").append(totalInputs)
          .append(" inputMask=0x").append(Integer.toHexString(inputMask)).append('\n');
        final ContextState.VAOState vao = (cs != null) ? cs.currentVao : null;
        final int dumpMask = inputMask | (vao != null ? vao.attribEnabledMask : 0);
        for (int i = 0; i < maxAttribs; i++) {
            if ((dumpMask & (1 << i)) == 0) continue;
            final boolean enabled = vao != null && vao.attribEnabled[i];
            final boolean shaderExpects = (shaderInputMask & (1 << i)) != 0;
            if (enabled) {
                final int b = vao.attribBinding[i];
                final int boundStride = vao.bindingStride[b];
                final int stride = boundStride != 0 ? boundStride : (vao.attribStride[i] != 0 ? vao.attribStride[i] : vao.attribSize[i] * GLTypes.sizeBytes(vao.attribType[i]));
                int fmt = mapVertexFormat(vao.attribSize[i], vao.attribType[i], vao.attribNormalized[i], vao.attribIsInteger[i]);
                if (i < shaderInputBaseType.length) fmt = correctSignedness(fmt, shaderInputBaseType[i]);
                final String tag = shaderExpects ? "VAO-enabled" : "VAO-enabled shader-extra (dropped)";
                sb.append("    attr[loc=").append(i).append("] ").append(tag).append(':')
                  .append(" size=").append(vao.attribSize[i])
                  .append(" type=0x").append(Integer.toHexString(vao.attribType[i]))
                  .append(" norm=").append(vao.attribNormalized[i])
                  .append(" iPtr=").append(vao.attribIsInteger[i])
                  .append(" relOff=").append(vao.attribRelativeOffset[i])
                  .append(" stride=").append(stride)
                  .append(" divisor=").append(vao.bindingDivisor[b])
                  .append(" sdlFmt=").append(fmt)
                  .append(" (").append(vertexFormatName(fmt)).append(")\n");
            } else if (shaderExpects) {
                final int vecSize = i < shaderInputVecSize.length ? shaderInputVecSize[i] : 4;
                final int fmt = mapVertexFormat(vecSize, GL11.GL_FLOAT, false, false);
                sb.append("    attr[loc=").append(i).append("] expansion-default:")
                  .append(" vecSize=").append(vecSize)
                  .append(" sdlFmt=").append(fmt)
                  .append(" (").append(vertexFormatName(fmt)).append(")\n");
            }
        }
        LOG.error(sb.toString());
    }

    private static String textureFormatName(int sdlFormat) {
        return switch (sdlFormat) {
            case 0 -> "INVALID";
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM -> "R8G8B8A8_UNORM";
            case SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM -> "B8G8R8A8_UNORM";
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB -> "R8G8B8A8_UNORM_SRGB";
            case SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB -> "B8G8R8A8_UNORM_SRGB";
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM -> "R8_UNORM";
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM -> "R8G8_UNORM";
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT -> "R16G16B16A16_FLOAT";
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT -> "R32G32B32A32_FLOAT";
            case SDL_GPU_TEXTUREFORMAT_D16_UNORM -> "D16_UNORM";
            case SDL_GPU_TEXTUREFORMAT_D24_UNORM -> "D24_UNORM";
            case SDL_GPU_TEXTUREFORMAT_D32_FLOAT -> "D32_FLOAT";
            case SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT -> "D24_UNORM_S8_UINT";
            case SDL_GPU_TEXTUREFORMAT_D32_FLOAT_S8_UINT -> "D32_FLOAT_S8_UINT";
            default -> "format#" + sdlFormat;
        };
    }

    private static String vertexFormatName(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_VERTEXELEMENTFORMAT_INVALID -> "INVALID";
            case SDL_GPU_VERTEXELEMENTFORMAT_FLOAT -> "FLOAT";
            case SDL_GPU_VERTEXELEMENTFORMAT_FLOAT2 -> "FLOAT2";
            case SDL_GPU_VERTEXELEMENTFORMAT_FLOAT3 -> "FLOAT3";
            case SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4 -> "FLOAT4";
            case SDL_GPU_VERTEXELEMENTFORMAT_BYTE2 -> "BYTE2";
            case SDL_GPU_VERTEXELEMENTFORMAT_BYTE4 -> "BYTE4";
            case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2 -> "UBYTE2";
            case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4 -> "UBYTE4";
            case SDL_GPU_VERTEXELEMENTFORMAT_BYTE2_NORM -> "BYTE2_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM -> "BYTE4_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2_NORM -> "UBYTE2_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM -> "UBYTE4_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_SHORT2 -> "SHORT2";
            case SDL_GPU_VERTEXELEMENTFORMAT_SHORT4 -> "SHORT4";
            case SDL_GPU_VERTEXELEMENTFORMAT_USHORT2 -> "USHORT2";
            case SDL_GPU_VERTEXELEMENTFORMAT_USHORT4 -> "USHORT4";
            case SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM -> "SHORT2_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_SHORT4_NORM -> "SHORT4_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_USHORT2_NORM -> "USHORT2_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_USHORT4_NORM -> "USHORT4_NORM";
            case SDL_GPU_VERTEXELEMENTFORMAT_HALF2 -> "HALF2";
            case SDL_GPU_VERTEXELEMENTFORMAT_HALF4 -> "HALF4";
            case SDL_GPU_VERTEXELEMENTFORMAT_INT -> "INT";
            case SDL_GPU_VERTEXELEMENTFORMAT_INT2 -> "INT2";
            case SDL_GPU_VERTEXELEMENTFORMAT_INT3 -> "INT3";
            case SDL_GPU_VERTEXELEMENTFORMAT_INT4 -> "INT4";
            case SDL_GPU_VERTEXELEMENTFORMAT_UINT -> "UINT";
            case SDL_GPU_VERTEXELEMENTFORMAT_UINT2 -> "UINT2";
            case SDL_GPU_VERTEXELEMENTFORMAT_UINT3 -> "UINT3";
            case SDL_GPU_VERTEXELEMENTFORMAT_UINT4 -> "UINT4";
            default -> "vfmt#" + sdlFormat;
        };
    }

    private static String intArrayToString(int[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        final StringBuilder sb = new StringBuilder(arr.length * 4 + 2);
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static final int GL_TYPE_BASE = GL11.GL_BYTE;
    static final int INVALID_FMT = -1;
    private static final int[][] VTX_FMT = buildVertexFormatTable();

    private static int[] fmtSame(int s1, int s2, int s3, int s4) {
        return new int[] { INVALID_FMT, s1, s2, s3, s4, INVALID_FMT, s1, s2, s3, s4 };
    }

    private static int[] fmt(int s1, int s2, int s3, int s4, int n1, int n2, int n3, int n4) {
        return new int[] { INVALID_FMT, s1, s2, s3, s4, INVALID_FMT, n1, n2, n3, n4 };
    }

    private static int[][] buildVertexFormatTable() {
        final int[][] t = new int[GL30.GL_HALF_FLOAT - GL_TYPE_BASE + 1][];
        // Float / int / uint -- all sizes 1..4 supported; normalized flag irrelevant for these
        t[GL11.GL_FLOAT - GL_TYPE_BASE] = fmtSame(
            SDL_GPU_VERTEXELEMENTFORMAT_FLOAT, SDL_GPU_VERTEXELEMENTFORMAT_FLOAT2,
            SDL_GPU_VERTEXELEMENTFORMAT_FLOAT3, SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4);
        t[GL11.GL_INT - GL_TYPE_BASE] = fmtSame(
            SDL_GPU_VERTEXELEMENTFORMAT_INT, SDL_GPU_VERTEXELEMENTFORMAT_INT2,
            SDL_GPU_VERTEXELEMENTFORMAT_INT3, SDL_GPU_VERTEXELEMENTFORMAT_INT4);
        t[GL11.GL_UNSIGNED_INT - GL_TYPE_BASE] = fmtSame(
            SDL_GPU_VERTEXELEMENTFORMAT_UINT, SDL_GPU_VERTEXELEMENTFORMAT_UINT2,
            SDL_GPU_VERTEXELEMENTFORMAT_UINT3, SDL_GPU_VERTEXELEMENTFORMAT_UINT4);
        // Half float -- only sizes 2 and 4 (no HALF1/HALF3 in SDL); normalized irrelevant
        t[GL30.GL_HALF_FLOAT - GL_TYPE_BASE] = fmtSame(
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_HALF2,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_HALF4);
        // Byte / ushort etc -- only sizes 2 and 4; raw and normalized halves differ
        t[GL11.GL_BYTE - GL_TYPE_BASE] = fmt(
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_BYTE2,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_BYTE4,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_BYTE2_NORM,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_BYTE4_NORM);
        t[GL11.GL_UNSIGNED_BYTE - GL_TYPE_BASE] = fmt(
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2_NORM,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4_NORM);
        t[GL11.GL_SHORT - GL_TYPE_BASE] = fmt(
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_SHORT2,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_SHORT4,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_SHORT2_NORM,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_SHORT4_NORM);
        t[GL11.GL_UNSIGNED_SHORT - GL_TYPE_BASE] = fmt(
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_USHORT2,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_USHORT4,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_USHORT2_NORM,
            INVALID_FMT, SDL_GPU_VERTEXELEMENTFORMAT_USHORT4_NORM);
        return t;
    }

    record VertexInputResult(SDL_GPUVertexBufferDescription.Buffer bindings, SDL_GPUVertexAttribute.Buffer attrs, int numBuffers, int numAttributes, long vertexShaderOverride) {}

    private static boolean isConvertibleIntegerType(int glType) {
        return glType == GL11.GL_BYTE || glType == GL11.GL_UNSIGNED_BYTE || glType == GL11.GL_SHORT || glType == GL11.GL_UNSIGNED_SHORT || glType == GL11.GL_INT || glType == GL11.GL_UNSIGNED_INT;
    }

    private static boolean isSignedType(int glType) {
        return glType == GL11.GL_BYTE || glType == GL11.GL_SHORT || glType == GL11.GL_INT;
    }

    private static int boundComponentCount(int size, int glType) {
        final int typeIdx = glType - GL_TYPE_BASE;
        if (typeIdx < 0 || typeIdx >= VTX_FMT.length || VTX_FMT[typeIdx] == null) return size;
        if (VTX_FMT[typeIdx][size] != INVALID_FMT) return size;
        return size == 1 ? 2 : 4;
    }

    private boolean isCoercedAttrib(ContextState.VAOState vao, int i) {
        if (vao.attribNormalized[i] || vao.attribIsInteger[i]) return false;
        if (!isConvertibleIntegerType(vao.attribType[i])) return false;
        if (i >= shaderInputBaseType.length) return false;
        return shaderInputScalarKind(shaderInputBaseType[i]) == ScalarKind.FLOAT;
    }

    private boolean attribLive(PipelineStore store, ContextState cs, ContextState.VAOState vao, int i) {
        if (!vao.attribEnabled[i]) return false;
        if (vao.attribType[i] == 0) {
            if (store.loggedTypeZeroAttrib.add(Hashing.packHiLo(cs.boundProgram, i))) {
                LOG.warn("[PipelineCache] vertex attrib location={} enabled with no pointer set (program={}); treating as disabled", i, cs.boundProgram);
            }
            return false;
        }
        final IntToLongFunction resolver = store.bufferHandleResolver();
        if (resolver != null) {
            final int bindingBuffer = vao.bindingBuffer[vao.attribBinding[i]];
            if (resolver.applyAsLong(bindingBuffer) == 0) {
                if (store.loggedDeadBufferAttrib.add(Hashing.packHiLo(cs.boundProgram, i))) {
                    LOG.warn("[PipelineCache] vertex attrib location={} enabled but binding buffer {} has no live handle (program={}); treating as disabled", i, bindingBuffer, cs.boundProgram);
                }
                return false;
            }
        }
        return true;
    }

    int computeInputMask() {
        return shaderInputMask;
    }

    VertexInputResult buildVertexInput(PipelineStore store, ContextState cs, MemoryStack stack) {
        final ContextState.VAOState vao = (cs != null) ? cs.currentVao : null;
        final int inputMask = computeInputMask();
        final int totalInputs = Integer.bitCount(inputMask);
        if (totalInputs == 0) {
            return new VertexInputResult(null, null, 0, 0, 0L);
        }

        int coercedMask = 0;
        long variantKey = 0L;
        if (vao != null) {
            for (int i = 0; i < maxAttribs; i++) {
                if ((inputMask & (1 << i)) == 0) continue;
                if (!attribLive(store, cs, vao, i)) continue;
                if (!isCoercedAttrib(vao, i)) continue;
                coercedMask |= (1 << i);
                variantKey = Hashing.fmix64(variantKey,
                    ((long) i << 40) | ((long) vao.attribType[i] << 8) | vao.attribSize[i]);
            }
        }

        long variantShader = 0L;
        int[] variantBaseType = null;
        if (coercedMask != 0) {
            final PipelineStore.VertexVariantResolver resolver = store.vertexVariantResolver();
            if (resolver != null) {
                final List<UscaledRetype.Attrib> attribs = new ArrayList<>(Integer.bitCount(coercedMask));
                for (int i = 0; i < maxAttribs; i++) {
                    if ((coercedMask & (1 << i)) == 0) continue;
                    final String name = i < shaderInputName.length ? shaderInputName[i] : null;
                    if (name == null) { attribs.clear(); break; }
                    attribs.add(new UscaledRetype.Attrib(name, i,
                        i < shaderInputVecSize.length ? shaderInputVecSize[i] : 4,
                        boundComponentCount(vao.attribSize[i], vao.attribType[i]),
                        isSignedType(vao.attribType[i])));
                }
                if (!attribs.isEmpty()) {
                    final ShaderManager.VertexVariant v = resolver.resolve(cs.boundProgram, variantKey, attribs);
                    if (v != null) {
                        variantShader = v.sdlShader;
                        variantBaseType = v.inputBaseType;
                    }
                }
            }
            if (variantShader == 0L) {
                for (int i = 0; i < maxAttribs; i++) {
                    if ((coercedMask & (1 << i)) == 0) continue;
                    warnUnconvertedAttrib(store, cs, vao, i);
                }
                coercedMask = 0;
            }
        }

        // Bindings array must stay dense, indexed by slot
        final int maxSlot = 31 - Integer.numberOfLeadingZeros(inputMask);
        final int numBuffers = maxSlot + 1;
        final int numAttributes = numBuffers;
        final SDL_GPUVertexAttribute.Buffer attrs = SDL_GPUVertexAttribute.calloc(numAttributes, stack);
        final SDL_GPUVertexBufferDescription.Buffer bindings = SDL_GPUVertexBufferDescription.calloc(numBuffers, stack);

        for (int i = 0; i < numBuffers; i++) {
            if ((inputMask & (1 << i)) == 0) {
                bindings.get(i)
                    .slot(i).pitch(16)
                    .input_rate(SDL_GPU_VERTEXINPUTRATE_INSTANCE).instance_step_rate(0);
                attrs.get(i)
                    .location(i).buffer_slot(i)
                    .format(SDL_GPU_VERTEXELEMENTFORMAT_FLOAT4)
                    .offset(0);
                continue;
            }
            final boolean enabled = vao != null && attribLive(store, cs, vao, i);

            if (enabled) {
                final int b = vao.attribBinding[i];
                final int boundStride = vao.bindingStride[b];
                final int stride = boundStride != 0 ? boundStride : (vao.attribStride[i] != 0 ? vao.attribStride[i] : vao.attribSize[i] * GLTypes.sizeBytes(vao.attribType[i]));
                final int divisor = vao.bindingDivisor[b];
                final int relOff = vao.attribRelativeOffset[i];
                final int attribAlign = GLTypes.sizeBytes(vao.attribType[i]);
                if (attribAlign > 0 && (stride % attribAlign) != 0) {
                    LOG.warn("[PipelineCache] unaligned vertex stride: location={} stride={} type=0x{} size={} -- attribAddress will not satisfy format alignment; fix producer", i, stride, Integer.toHexString(vao.attribType[i]), vao.attribSize[i]);
                }
                bindings.get(i)
                    .slot(i).pitch(stride)
                    .input_rate(divisor > 0 ? SDL_GPU_VERTEXINPUTRATE_INSTANCE : SDL_GPU_VERTEXINPUTRATE_VERTEX)
                    .instance_step_rate(0);
                final boolean coerced = (coercedMask & (1 << i)) != 0;
                int fmt = mapVertexFormat(vao.attribSize[i], vao.attribType[i], vao.attribNormalized[i], coerced || vao.attribIsInteger[i]);
                final int[] baseTypes = (coerced && variantBaseType != null) ? variantBaseType : shaderInputBaseType;
                if (i < baseTypes.length) {
                    fmt = correctSignedness(fmt, baseTypes[i]);
                    final ScalarKind shaderKind = shaderInputScalarKind(baseTypes[i]);
                    if (shaderKind != null && shaderKind != formatScalarKind(fmt)) {
                        final long key = Hashing.packHiLo(cs.boundProgram, i);
                        if (store.loggedMismatch.add(key)) {
                            LOG.warn("Vertex format mismatch: program={} location={} producer-format=0x{} (size={} type=0x{} norm={} iPtr={}) shader expects baseType={} vecSize={}; skipping draw",
                                cs.boundProgram, i, Integer.toHexString(fmt),
                                vao.attribSize[i], Integer.toHexString(vao.attribType[i]),
                                vao.attribNormalized[i], vao.attribIsInteger[i],
                                baseTypes[i],
                                i < shaderInputVecSize.length ? shaderInputVecSize[i] : -1);
                        }
                        return null;
                    }
                }
                if (attribAlign > 0) {
                    final int span = boundComponentCount(vao.attribSize[i], vao.attribType[i]) * attribAlign;
                    if (relOff + span > stride) {
                        final long key = Hashing.packHiLo(cs.boundProgram, i);
                        if (store.loggedFormatSubstitution.add(key)) {
                            LOG.warn("[PipelineCache] vertex attribute size promoted past the vertex: program={} location={} requested size={} type={} -> bound {} reading {} bytes at offset {} with stride {}",
                                cs.boundProgram, i, vao.attribSize[i], GLTypes.name(vao.attribType[i]), vertexFormatName(fmt), span, relOff, stride);
                        }
                    }
                }
                attrs.get(i)
                    .location(i).buffer_slot(i)
                    .format(fmt)
                    .offset(relOff);
            } else {
                final int vecSize = i < shaderInputVecSize.length ? shaderInputVecSize[i] : 4;
                bindings.get(i)
                    .slot(i).pitch(16) // sizeof(vec4), one element
                    .input_rate(SDL_GPU_VERTEXINPUTRATE_INSTANCE).instance_step_rate(0);
                attrs.get(i)
                    .location(i).buffer_slot(i)
                    .format(mapVertexFormat(vecSize, GL11.GL_FLOAT, false, false))
                    .offset(0);
            }
        }
        return new VertexInputResult(bindings, attrs, numBuffers, numAttributes, variantShader);
    }

    private void warnUnconvertedAttrib(PipelineStore store, ContextState cs, ContextState.VAOState vao, int i) {
        final long key = Hashing.packHiLo(cs.boundProgram, i);
        if (!store.loggedFormatSubstitution.add(key)) return;
        final int used = mapVertexFormat(vao.attribSize[i], vao.attribType[i], vao.attribNormalized[i], false);
        LOG.warn("[PipelineCache] vertex attribute coerced to normalized: program={} location={} name={} requested size={} type={} normalized=false -> using {}. SDL GPU has no USCALED format and the integer-conversion variant could not be built, so the shader sees values divided by the type maximum instead of the original range. Declare the attribute GL_FLOAT, or use glVertexAttribIPointer with an integer shader input.",
            cs.boundProgram, i, i < shaderInputName.length ? shaderInputName[i] : "?", vao.attribSize[i], GLTypes.name(vao.attribType[i]), vertexFormatName(used));
    }

    public static int mapVertexFormat(int size, int glType, boolean normalized, boolean isInteger) {
        if (size < 1 || size > 4) {
            throw new IllegalArgumentException("Invalid vertex attrib size " + size + " (must be 1-4)");
        }
        if (!isInteger && !normalized && glType != GL11.GL_FLOAT && glType != GL30.GL_HALF_FLOAT) {
            normalized = true;
        }
        final int typeIdx = glType - GL_TYPE_BASE;
        if (typeIdx < 0 || typeIdx >= VTX_FMT.length || VTX_FMT[typeIdx] == null) {
            throw new IllegalArgumentException("Unsupported vertex attrib GL type 0x" + Integer.toHexString(glType) + " (size=" + size + ", normalized=" + normalized + ")");
        }
        final int idxBase = normalized ? 5 : 0;
        int fmt = VTX_FMT[typeIdx][idxBase + size];
        if (fmt == INVALID_FMT) {
            final int promoted = size == 1 ? 2 : 4;
            fmt = VTX_FMT[typeIdx][idxBase + promoted];
            if (fmt == INVALID_FMT) {
                throw new IllegalArgumentException("SDL GPU has no format for size=" + size + " type=0x" + Integer.toHexString(glType) + " normalized=" + normalized);
            }
        }
        return fmt;
    }

    public static int requestedVertexFormat(int size, int glType, boolean normalized) {
        if (size < 1 || size > 4) return INVALID_FMT;
        final int typeIdx = glType - GL_TYPE_BASE;
        if (typeIdx < 0 || typeIdx >= VTX_FMT.length || VTX_FMT[typeIdx] == null) return INVALID_FMT;
        return VTX_FMT[typeIdx][(normalized ? 5 : 0) + size];
    }

    /** Correct vertex format signedness to match the shader's expected base type. */
    public static int correctSignedness(int sdlFormat, int shaderBaseType) {
        if (shaderBaseType == Spvc.SPVC_BASETYPE_INT32) {
            return switch (sdlFormat) {
                case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2 -> SDL_GPU_VERTEXELEMENTFORMAT_BYTE2;
                case SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4 -> SDL_GPU_VERTEXELEMENTFORMAT_BYTE4;
                case SDL_GPU_VERTEXELEMENTFORMAT_USHORT2 -> SDL_GPU_VERTEXELEMENTFORMAT_SHORT2;
                case SDL_GPU_VERTEXELEMENTFORMAT_USHORT4 -> SDL_GPU_VERTEXELEMENTFORMAT_SHORT4;
                case SDL_GPU_VERTEXELEMENTFORMAT_UINT -> SDL_GPU_VERTEXELEMENTFORMAT_INT;
                case SDL_GPU_VERTEXELEMENTFORMAT_UINT2 -> SDL_GPU_VERTEXELEMENTFORMAT_INT2;
                case SDL_GPU_VERTEXELEMENTFORMAT_UINT3 -> SDL_GPU_VERTEXELEMENTFORMAT_INT3;
                case SDL_GPU_VERTEXELEMENTFORMAT_UINT4 -> SDL_GPU_VERTEXELEMENTFORMAT_INT4;
                default -> sdlFormat;
            };
        } else if (shaderBaseType == Spvc.SPVC_BASETYPE_UINT32) {
            return switch (sdlFormat) {
                case SDL_GPU_VERTEXELEMENTFORMAT_BYTE2 -> SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2;
                case SDL_GPU_VERTEXELEMENTFORMAT_BYTE4 -> SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4;
                case SDL_GPU_VERTEXELEMENTFORMAT_SHORT2 -> SDL_GPU_VERTEXELEMENTFORMAT_USHORT2;
                case SDL_GPU_VERTEXELEMENTFORMAT_SHORT4 -> SDL_GPU_VERTEXELEMENTFORMAT_USHORT4;
                case SDL_GPU_VERTEXELEMENTFORMAT_INT -> SDL_GPU_VERTEXELEMENTFORMAT_UINT;
                case SDL_GPU_VERTEXELEMENTFORMAT_INT2 -> SDL_GPU_VERTEXELEMENTFORMAT_UINT2;
                case SDL_GPU_VERTEXELEMENTFORMAT_INT3 -> SDL_GPU_VERTEXELEMENTFORMAT_UINT3;
                case SDL_GPU_VERTEXELEMENTFORMAT_INT4 -> SDL_GPU_VERTEXELEMENTFORMAT_UINT4;
                default -> sdlFormat;
            };
        }
        return sdlFormat;
    }

    private enum ScalarKind { FLOAT, SINT, UINT }

    private static ScalarKind formatScalarKind(int sdlFormat) {
        return switch (sdlFormat) {
            case SDL_GPU_VERTEXELEMENTFORMAT_INT,
                 SDL_GPU_VERTEXELEMENTFORMAT_INT2,
                 SDL_GPU_VERTEXELEMENTFORMAT_INT3,
                 SDL_GPU_VERTEXELEMENTFORMAT_INT4,
                 SDL_GPU_VERTEXELEMENTFORMAT_BYTE2,
                 SDL_GPU_VERTEXELEMENTFORMAT_BYTE4,
                 SDL_GPU_VERTEXELEMENTFORMAT_SHORT2,
                 SDL_GPU_VERTEXELEMENTFORMAT_SHORT4 -> ScalarKind.SINT;
            case SDL_GPU_VERTEXELEMENTFORMAT_UINT,
                 SDL_GPU_VERTEXELEMENTFORMAT_UINT2,
                 SDL_GPU_VERTEXELEMENTFORMAT_UINT3,
                 SDL_GPU_VERTEXELEMENTFORMAT_UINT4,
                 SDL_GPU_VERTEXELEMENTFORMAT_UBYTE2,
                 SDL_GPU_VERTEXELEMENTFORMAT_UBYTE4,
                 SDL_GPU_VERTEXELEMENTFORMAT_USHORT2,
                 SDL_GPU_VERTEXELEMENTFORMAT_USHORT4 -> ScalarKind.UINT;
            default -> ScalarKind.FLOAT;
        };
    }

    private static ScalarKind shaderInputScalarKind(int spvcBaseType) {
        if (spvcBaseType == Spvc.SPVC_BASETYPE_INT32)  return ScalarKind.SINT;
        if (spvcBaseType == Spvc.SPVC_BASETYPE_UINT32) return ScalarKind.UINT;
        if (spvcBaseType == Spvc.SPVC_BASETYPE_FP32 || spvcBaseType == Spvc.SPVC_BASETYPE_FP16) return ScalarKind.FLOAT;
        return null;
    }

}
