package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.GLContextState;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
            GLStateManager.glBindVertexArray(vao);
            return;
        }
        final int prevVBO = GLStateManager.getBoundVBO();
        final int v = GLStateManager.glGenVertexArrays();
        GLStateManager.glBindVertexArray(v);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, sharedVBO);
        final int stride = layout.stride();
        for (int i = 0; i < layout.locations.length; i++) {
            final int loc = layout.locations[i];
            GLStateManager.glVertexAttribPointer(loc, layout.sizes[i], layout.types[i], layout.normalized[i], stride, layout.offset(i));
            GLStateManager.glEnableVertexAttribArray(loc);
        }
        GLStateManager.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, sharedEBO);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
        vao = v;
        vaoCtx = glCtx;
    }

    public void delete() {
        if (deleted) return;
        deleted = true;
        if (vaoCtx == GLStateManager.ctx()) GLStateManager.glDeleteVertexArrays(vao);
        if (sharedVBO != 0) GLStateManager.glDeleteBuffers(sharedVBO);
        if (sharedEBO != 0) GLStateManager.glDeleteBuffers(sharedEBO);
        vao = sharedVBO = sharedEBO = 0;
        vaoCtx = null;
    }

    @Override
    public String toString() {
        return "IndexedDrawBatch(vao=" + vao + ", vbo=" + sharedVBO + ", ebo=" + sharedEBO + ")";
    }
}
