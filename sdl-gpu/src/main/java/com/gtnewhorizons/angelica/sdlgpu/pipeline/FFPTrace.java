package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.FFPUniformBlock;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentMapping;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class FFPTrace {
    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");
    private static final String PREFIX = "[FFPTrace] ";
    private static final int MAX_KEYS = 512;

    private final ResourceManager resourceManager;
    private final LongOpenHashSet seenKeys = new LongOpenHashSet();
    private final StringBuilder sb = new StringBuilder(1024);
    private int drawsThisFrame;
    private boolean capReported;

    public FFPTrace(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public synchronized void trace(ContextState st, String kind, int mode, int count, int indexType, int first, int baseVertex) {
        drawsThisFrame++;
        final long key = key(st);
        if (seenKeys.contains(key)) return;
        if (seenKeys.size() >= MAX_KEYS) {
            if (!capReported) {
                capReported = true;
                LOG.info("{}key cap {} reached; no further distinct draw states will be logged", PREFIX, MAX_KEYS);
            }
            return;
        }
        seenKeys.add(key);
        LOG.info(format(st, kind, mode, count, indexType, first, baseVertex));
    }

    public synchronized void frameEnd(FrameManager.FrameState f) {
        LOG.info("{}frame {} end: draws={} dropped={}", PREFIX, f.frameNumber, drawsThisFrame, f.droppedDrawsThisFrame);
        drawsThisFrame = 0;
    }

    private long key(ContextState st) {
        final PipelineCache p = st.pipeline;
        long h = p.lastKey();
        h = Hashing.fmix64(h, st.boundFboId);
        h = Hashing.fmix64(h, Hashing.packHiLo(st.boundTextures[0], st.boundTextures[1]));
        h = Hashing.fmix64(h, Hashing.packHiLo(st.scissorEnabled ? 1 : 0, 0));
        h = Hashing.fmix64(h, Hashing.packHiLo(Float.floatToRawIntBits(st.viewportX), Float.floatToRawIntBits(st.viewportY)));
        h = Hashing.fmix64(h, Hashing.packHiLo(Float.floatToRawIntBits(st.viewportW), Float.floatToRawIntBits(st.viewportH)));
        h = Hashing.fmix64(h, Hashing.packHiLo(st.scissorX, st.scissorY));
        h = Hashing.fmix64(h, Hashing.packHiLo(st.scissorW, st.scissorH));
        return h;
    }

    private String format(ContextState st, String kind, int mode, int count, int indexType, int first, int baseVertex) {
        final PipelineCache p = st.pipeline;
        final ContextState.VAOState vao = st.currentVao;
        final ShaderManager.ProgramObject prog = st.boundProgramObj;
        sb.setLength(0);
        sb.append(PREFIX);
        sb.append("kind=").append(kind);
        sb.append(" mode=0x").append(Integer.toHexString(mode));
        sb.append(" count=").append(count);
        if (indexType != 0) sb.append(" indexType=0x").append(Integer.toHexString(indexType));
        sb.append(" first=").append(first).append(" baseVertex=").append(baseVertex);

        sb.append(" | prog=").append(st.boundProgram);
        if (prog == null) {
            sb.append(" progObj=null");
        } else {
            sb.append(" ffpUboBinding=").append(prog.externalUboBinding);
            sb.append(" fragSamplers=").append(prog.fragmentSamplerNames);
            sb.append(" vsInputs=[");
            boolean firstName = true;
            for (int i = 0; i < prog.vertexInputName.length; i++) {
                final String n = prog.vertexInputName[i];
                if (n == null) continue;
                if (!firstName) sb.append(',');
                firstName = false;
                sb.append(i).append(':').append(n);
            }
            sb.append(']');
        }

        sb.append(" | fbo=").append(st.boundFboId);
        sb.append(" vp=(").append(st.viewportX).append(',').append(st.viewportY).append(',')
            .append(st.viewportW).append(',').append(st.viewportH).append(')');
        sb.append(" scissor=").append(st.scissorEnabled);
        if (st.scissorEnabled) {
            sb.append('(').append(st.scissorX).append(',').append(st.scissorY).append(',')
                .append(st.scissorW).append(',').append(st.scissorH).append(')');
        }
        sb.append(" cull=").append(p.cullEnabled).append("/mode=").append(p.cullFaceMode)
            .append("/front=").append(p.frontFace).append("/all=").append(p.cullAll);
        sb.append(" depth=").append(p.depthTestEnabled).append("/func=").append(p.depthCompareOp)
            .append("/write=").append(p.depthWriteEnabled);
        sb.append(" blend=").append(p.blendEnabledPerAttachment[0])
            .append("/src=").append(p.srcColorFactor).append("/dst=").append(p.dstColorFactor)
            .append("/srcA=").append(p.srcAlphaFactor).append("/dstA=").append(p.dstAlphaFactor)
            .append("/op=").append(p.colorBlendOp).append('/').append(p.alphaBlendOp);
        sb.append(" colorMask=0x").append(Integer.toHexString(p.colorWriteMask));
        sb.append(" primitiveType=").append(p.primitiveType);

        appendTextureUnit(st, 0);
        appendTextureUnit(st, 1);

        sb.append(" | attribs=[");
        int enabled = vao.attribEnabledMask;
        boolean firstAttrib = true;
        while (enabled != 0) {
            final int i = Integer.numberOfTrailingZeros(enabled);
            enabled &= enabled - 1;
            final int b = vao.attribBinding[i];
            if (!firstAttrib) sb.append(' ');
            firstAttrib = false;
            sb.append(i).append(":size=").append(vao.attribSize[i])
                .append(",type=0x").append(Integer.toHexString(vao.attribType[i]))
                .append(",norm=").append(vao.attribNormalized[i])
                .append(",int=").append(vao.attribIsInteger[i])
                .append(",stride=").append(vao.attribStride[i])
                .append(",off=").append(vao.attribOffset[i])
                .append(",buf=").append(vao.bindingBuffer[b])
                .append(",bufOff=").append(vao.bindingOffset[b]);
        }
        sb.append("] enabledMask=0x").append(Integer.toHexString(vao.attribEnabledMask))
            .append(" shaderInputMask=0x").append(Integer.toHexString(p.shaderInputMask))
            .append(" ebo=").append(vao.elementBuffer);

        appendFfpUniforms(st, prog);
        return sb.toString();
    }

    private void appendTextureUnit(ContextState st, int unit) {
        final int id = st.boundTextures[unit];
        sb.append(" | tex").append(unit).append("=").append(id);
        if (id != 0) {
            sb.append(" handle=").append(resourceManager.getTextureHandle(id) != 0);
            final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(id);
            if (meta == null) {
                sb.append(" meta=null");
            } else {
                sb.append(" size=").append(meta.width()).append('x').append(meta.height())
                    .append(" levels=").append(meta.levels())
                    .append(" sdlFormat=").append(meta.sdlFormat())
                    .append(" glFormat=0x").append(Integer.toHexString(meta.glFormat()));
            }
        }
        sb.append(" ffpEnabled=").append(GLStateManager.getTextures().getTextureUnitStates(unit).isEnabled());
    }

    private void appendFfpUniforms(ContextState st, ShaderManager.ProgramObject prog) {
        if (prog == null) return;
        final int binding = prog.externalUboBinding;
        if (binding < 0 || binding >= ContextState.MAX_INDEXED_BUFFERS) return;
        final int glId = st.boundUboByIndex[binding];
        if (glId == 0) {
            sb.append(" | ffpUbo=unbound");
            return;
        }
        final PersistentMapping pm = resourceManager.getPersistentMapping(glId);
        final ByteBuffer buf = pm != null ? pm.staging : resourceManager.getUboShadow(glId);
        if (buf == null) {
            sb.append(" | ffpUbo=buffer").append(glId).append(" noStaging");
            return;
        }
        final int base = st.uboRangeOffset[binding];
        sb.append(" | ffpUbo=buffer").append(glId).append(" base=").append(base);
        appendUboFloat(buf, base, FFPUniformBlock.ALPHA_REF, " u_AlphaRef=");
        appendUboMatrix(buf, base, FFPUniformBlock.MVP_MATRIX, " u_MVPMatrix=");
        appendUboMatrix(buf, base, FFPUniformBlock.LIGHTMAP_TEXTURE_MATRIX, " u_LightmapTextureMatrix=");
    }

    private static float floatIgnoringLimit(ByteBuffer buf, int at) {
        return MemoryUtil.memGetFloat(MemoryUtil.memAddress0(buf) + at);
    }

    private void appendUboFloat(ByteBuffer buf, int base, int memberOffset, String label) {
        final int at = base + memberOffset;
        sb.append(label);
        if (at < 0 || at + 4 > buf.capacity()) {
            sb.append("oob");
            return;
        }
        sb.append(floatIgnoringLimit(buf, at));
    }

    private void appendUboMatrix(ByteBuffer buf, int base, int memberOffset, String label) {
        final int at = base + memberOffset;
        sb.append(label);
        if (at < 0 || at + 64 > buf.capacity()) {
            sb.append("oob");
            return;
        }
        sb.append('[');
        for (int i = 0; i < 16; i++) {
            if (i > 0) sb.append(',');
            sb.append(floatIgnoringLimit(buf, at + i * 4));
        }
        sb.append(']');
    }
}
