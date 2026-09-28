package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager.FrameState;
import com.gtnewhorizons.angelica.sdlgpu.resource.FboState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureOps;
import com.gtnewhorizons.angelica.sdlgpu.shader.LogicOpFormats;
import com.gtnewhorizons.angelica.sdlgpu.shader.LogicOpVariant;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;

public final class LogicOpEmulator {
    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final ShaderManager shaderManager;
    private final PipelineApplier pipelineApplier;

    LogicOpEmulator(FrameManager frameManager, ResourceManager resourceManager, ShaderManager shaderManager, PipelineApplier pipelineApplier) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.shaderManager = shaderManager;
        this.pipelineApplier = pipelineApplier;
    }

    public void beforeDraw(ContextState st, FrameState f) {
        if (!st.logicOpEnabled && st.appliedLogicOpKey == 0) return;
        final FboState fbo = st.boundFboId != 0 ? resourceManager.getFbo(st.boundFboId) : null;
        final long key = st.logicOpEnabled ? computeKey(st, fbo) : 0L;
        if (key != 0L) copyDestinations(st, f, fbo, key);
        if (key != st.appliedLogicOpKey) applyVariant(st, key);
    }

    private long computeKey(ContextState st, FboState fbo) {
        final int op = LogicOpFormats.mesaOp(st.logicOpMode);
        if (op == LogicOpFormats.OP_COPY || st.boundProgram == 0) return 0L;
        if (st.boundFboId != 0 && fbo == null) return 0L;
        final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
        if (prog == null || !prog.linked) return 0L;
        final int limit = Math.min(targetCount(fbo), prog.maxFragOutputLocation + 1);
        long key = op;
        boolean any = false;
        for (int loc = 0; loc < limit; loc++) {
            if (targetTexture(fbo, loc) == 0L) continue;
            final int cls = LogicOpFormats.classOf(targetFormat(fbo, loc));
            if (cls == 0) continue;
            key = LogicOpFormats.withClass(key, loc, cls);
            any = true;
        }
        return any ? key : 0L;
    }

    private void copyDestinations(ContextState st, FrameState f, FboState fbo, long key) {
        pipelineApplier.ensureRenderPass(st, f);
        if (f.renderPass == 0) return;
        long cp = 0L;
        for (int loc = 0; loc < ContextState.MAX_COLOR_ATTACHMENTS; loc++) {
            if (LogicOpFormats.classAt(key, loc) == 0) continue;
            final long tex = targetTexture(fbo, loc);
            final int w = targetWidth(fbo, loc);
            final int h = targetHeight(fbo, loc);
            final int fmt = targetFormat(fbo, loc);
            long scratch = st.logicOpScratch[loc];
            if (scratch == 0L || st.logicOpScratchFormat[loc] != fmt || st.logicOpScratchWidth[loc] != w || st.logicOpScratchHeight[loc] != h) {
                if (scratch != 0L) resourceManager.releaseTextureDeferred(scratch);
                scratch = resourceManager.createLogicOpScratch(fmt, w, h);
                st.logicOpScratch[loc] = scratch;
                st.logicOpScratchFormat[loc] = fmt;
                st.logicOpScratchWidth[loc] = w;
                st.logicOpScratchHeight[loc] = h;
                st.samplerBindGen++;
            }
            if (cp == 0L) cp = frameManager.ensureCopyPass(FrameManager.PASS_END_LOGIC_OP);
            TextureOps.copyTexture(cp, tex, 0, 0, scratch, 0, 0, 0, w, h);
        }
    }

    private void applyVariant(ContextState st, long key) {
        if (key == 0L) {
            final ShaderManager.ProgramObject prog = shaderManager.getProgram(st.boundProgram);
            st.pipeline.fragmentShader = prog != null ? prog.sdlFragmentShader : 0L;
            st.activeLogicOpVariant = null;
        } else {
            final LogicOpVariant v = shaderManager.getOrBuildLogicOpVariant(st.boundProgram, key);
            st.pipeline.fragmentShader = v.sdlShader;
            st.activeLogicOpVariant = v;
        }
        st.pipeline.markShaderDirty();
        st.appliedLogicOpKey = key;
        st.samplerBindGen++;
    }

    private static int targetCount(FboState fbo) {
        return fbo == null ? 1 : Math.min(fbo.drawBuffers.length, ContextState.MAX_COLOR_ATTACHMENTS);
    }

    private static int drawBuffer(FboState fbo, int loc) {
        final int db = fbo.drawBuffers[loc];
        return db >= 0 && db < ContextState.MAX_COLOR_ATTACHMENTS && fbo.colorTextures[db] != 0L ? db : -1;
    }

    private long targetTexture(FboState fbo, int loc) {
        if (fbo == null) return frameManager.getFbo0Texture();
        final int db = drawBuffer(fbo, loc);
        return db >= 0 ? fbo.colorTextures[db] : 0L;
    }

    private int targetFormat(FboState fbo, int loc) {
        return fbo == null ? frameManager.getSwapchainFormat() : fbo.colorFormats[drawBuffer(fbo, loc)];
    }

    private int targetWidth(FboState fbo, int loc) {
        if (fbo == null) return frameManager.getFbo0Width();
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(fbo.colorGlIds[drawBuffer(fbo, loc)]);
        return meta != null ? meta.width() : fbo.width;
    }

    private int targetHeight(FboState fbo, int loc) {
        if (fbo == null) return frameManager.getFbo0Height();
        final ResourceManager.TextureMeta meta = resourceManager.getTextureMeta(fbo.colorGlIds[drawBuffer(fbo, loc)]);
        return meta != null ? meta.height() : fbo.height;
    }
}
