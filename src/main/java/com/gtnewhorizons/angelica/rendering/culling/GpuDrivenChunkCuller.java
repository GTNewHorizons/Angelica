package com.gtnewhorizons.angelica.rendering.culling;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GpuDrivenChunkCuller {
    private static final Logger LOG = LogManager.getLogger("Angelica-Culling");
    private static final String COMPUTE_SHADER_RESOURCE = "angelica:culling/chunk_cull.csh";

    public static final int VISIBLE_ENTRY_BYTES = 8; // uvec2 per visible section
    static final int INDIRECT_COMMAND_BYTES = 20; // count, instCount, firstIdx, baseVtx, baseInst
    static final int FACINGS_PER_SECTION = 7;
    static final int LOCAL_SIZE_X = 64;
    private static final int INITIAL_META_SECTIONS = 4096;

    static final int VISIBLE_SSBO_BINDING = 0;
    static final int META_SSBO_BINDING = 1;
    static final int INDIRECT_SSBO_BINDING = 2;
    static final int FRUSTUM_UBO_BINDING = 1;

    private boolean ready;
    private boolean disabled;

    private GlProgram<Void> program;
    private int metaSsboGlId;
    private int frustumUboGlId;

    private int metaCapacityBytes;

    private int savedProgram;
    private int savedVisibleSsbo;
    private int savedMetaSsbo;
    private int savedIndirectSsbo;
    private int savedFrustumUbo;

    public boolean ensureReady() {
        if (ready) return true;
        if (disabled) return false;
        if (!RenderSystem.supportsCompute()) {
            disabled = true;
            LOG.info("GpuDrivenChunkCuller disabled: GL_ARB_compute_shader not supported");
            return false;
        }
        final GlShader cs = ShaderLoader.loadShader(ShaderType.COMPUTE, COMPUTE_SHADER_RESOURCE, ShaderConstants.EMPTY);
        try {
            program = GlProgram.builder("chunk_cull").attachShader(cs).link(ctx -> null);
        } finally {
            cs.delete();
        }
        metaSsboGlId   = GLSMBuffers.glGenBuffers();
        frustumUboGlId = GLSMBuffers.glGenBuffers();

        GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, frustumUboGlId);
        GLSMBuffers.glBufferData(GL31.GL_UNIFORM_BUFFER, FrustumExtractor.UBO_SIZE_BYTES, GL15.GL_STREAM_DRAW);

        metaCapacityBytes = SectionMetaBuffer.BYTES_PER_SECTION * INITIAL_META_SECTIONS;
        GLSMBuffers.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, metaSsboGlId);
        GLSMBuffers.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, ByteBuffer.allocateDirect(metaCapacityBytes).order(ByteOrder.nativeOrder()), GL15.GL_DYNAMIC_DRAW);

        ready = true;
        return true;
    }

    public boolean uploadSectionMeta(ByteBuffer metaBytes) {
        if (!ready || metaBytes == null || metaBytes.remaining() == 0) return false;
        final int needed = metaBytes.remaining();
        GLSMBuffers.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, metaSsboGlId);
        if (needed > metaCapacityBytes) {
            final int newCap = Math.max(needed, Math.max(metaCapacityBytes * 2, SectionMetaBuffer.BYTES_PER_SECTION * INITIAL_META_SECTIONS));
            GLSMBuffers.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, newCap, GL15.GL_DYNAMIC_DRAW);
            metaCapacityBytes = newCap;
        }
        GLSMBuffers.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, metaBytes);
        return true;
    }

    public void uploadFrustum(ByteBuffer frustumUboBytes) {
        if (!ready || frustumUboBytes == null) return;
        GLSMBuffers.glBindBuffer(GL31.GL_UNIFORM_BUFFER, frustumUboGlId);
        GLSMBuffers.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, frustumUboBytes);
    }

    void beginDispatches(int visibleSsboGlId, int indirectSsboGlId) {
        savedProgram = GLSMPrograms.getActiveProgram();
        savedVisibleSsbo  = RenderSystem.getIndexedBufferBinding(GL43.GL_SHADER_STORAGE_BUFFER, VISIBLE_SSBO_BINDING);
        savedMetaSsbo     = RenderSystem.getIndexedBufferBinding(GL43.GL_SHADER_STORAGE_BUFFER, META_SSBO_BINDING);
        savedIndirectSsbo = RenderSystem.getIndexedBufferBinding(GL43.GL_SHADER_STORAGE_BUFFER, INDIRECT_SSBO_BINDING);
        savedFrustumUbo   = RenderSystem.getIndexedBufferBinding(GL31.GL_UNIFORM_BUFFER,        FRUSTUM_UBO_BINDING);
        GLSMPrograms.glUseProgram(program.handle());
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, VISIBLE_SSBO_BINDING,   visibleSsboGlId);
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, META_SSBO_BINDING,      metaSsboGlId);
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, INDIRECT_SSBO_BINDING,  indirectSsboGlId);
        RenderSystem.bindBufferBase(GL31.GL_UNIFORM_BUFFER,        FRUSTUM_UBO_BINDING,    frustumUboGlId);
    }

    void dispatch(int visibleCount) {
        final int groups = (visibleCount + LOCAL_SIZE_X - 1) / LOCAL_SIZE_X;
        GLSMPrograms.glDispatchCompute(groups, 1, 1);
    }

    void endDispatches() {
        GLSMBuffers.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL42.GL_COMMAND_BARRIER_BIT);
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, VISIBLE_SSBO_BINDING,   savedVisibleSsbo);
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, META_SSBO_BINDING,      savedMetaSsbo);
        RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, INDIRECT_SSBO_BINDING,  savedIndirectSsbo);
        RenderSystem.bindBufferBase(GL31.GL_UNIFORM_BUFFER,        FRUSTUM_UBO_BINDING,    savedFrustumUbo);
        GLSMPrograms.glUseProgram(savedProgram);
    }

    public synchronized void shutdown() {
        if (program != null) { program.delete(); program = null; }
        if (metaSsboGlId   != 0) { GLSMBuffers.glDeleteBuffers(metaSsboGlId);   metaSsboGlId   = 0; }
        if (frustumUboGlId != 0) { GLSMBuffers.glDeleteBuffers(frustumUboGlId); frustumUboGlId = 0; }
        ready = false;
        disabled = true;
    }
}
