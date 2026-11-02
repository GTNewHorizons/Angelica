package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import org.lwjgl.opengl.GL15;

public final class IndexedDrawBatch {
    private final AttribLayoutKey layout;
    private int sharedVBO;
    private int sharedEBO;
    private int vao;
    private GLContextState vaoCtx;
    private boolean deleted;

    public IndexedDrawBatch(AttribLayoutKey layout, int sharedVBO, int sharedEBO) {
        this.layout = layout;
        this.sharedVBO = sharedVBO;
        this.sharedEBO = sharedEBO;
    }

    public int getSharedVBO() { return sharedVBO; }
    public int getSharedEBO() { return sharedEBO; }

    public void bind() {
        final GLContextState glCtx = GLStateManager.ctx();
        if (vaoCtx == glCtx) {
            GLSMVertexInput.glBindVertexArray(vao);
            return;
        }
        final int prevVBO = GLSMBuffers.getBoundVBO();
        final int v = GLSMVertexInput.glGenVertexArrays();
        GLSMVertexInput.glBindVertexArray(v);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, sharedVBO);
        final int stride = layout.stride();
        int vertexFlags = 0;
        for (int i = 0; i < layout.locations.length; i++) {
            final int loc = layout.locations[i];
            GLSMVertexInput.glVertexAttribPointer(loc, layout.sizes[i], layout.types[i], layout.normalized[i], stride, layout.offset(i));
            GLSMVertexInput.glEnableVertexAttribArray(loc);
            vertexFlags |= vertexFlag(loc);
        }
        // glVertexAttribPointer doesn't set these, and the FFP shader only reads the inputs they name. Saved with the VAO.
        VAOManager.setCurrentVertexFlags(vertexFlags);
        GLSMBuffers.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEBO);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        vao = v;
        vaoCtx = glCtx;
    }

    private static int vertexFlag(int location) {
        if (location == Usage.COLOR.getAttributeLocation()) return VertexFlags.COLOR_BIT;
        if (location == Usage.NORMAL.getAttributeLocation()) return VertexFlags.NORMAL_BIT;
        if (location == Usage.PRIMARY_UV.getAttributeLocation()) return VertexFlags.TEXTURE_BIT;
        if (location == Usage.SECONDARY_UV.getAttributeLocation()) return VertexFlags.BRIGHTNESS_BIT;
        return 0;
    }

    public void delete() {
        if (deleted) return;
        deleted = true;
        if (vaoCtx == GLStateManager.ctx()) GLSMVertexInput.glDeleteVertexArrays(vao);
        if (sharedVBO != 0) GLSMBuffers.glDeleteBuffers(sharedVBO);
        if (sharedEBO != 0) GLSMBuffers.glDeleteBuffers(sharedEBO);
        vao = sharedVBO = sharedEBO = 0;
        vaoCtx = null;
    }

    @Override
    public String toString() {
        return "IndexedDrawBatch(vao=" + vao + ", vbo=" + sharedVBO + ", ebo=" + sharedEBO + ")";
    }
}
