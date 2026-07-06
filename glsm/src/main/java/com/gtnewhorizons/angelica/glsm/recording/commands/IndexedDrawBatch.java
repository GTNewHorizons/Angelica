package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

public final class IndexedDrawBatch {
    private int sharedVAO;
    private int sharedVBO;
    private int sharedEBO;
    private boolean deleted;

    public IndexedDrawBatch(int sharedVAO, int sharedVBO, int sharedEBO) {
        this.sharedVAO = sharedVAO;
        this.sharedVBO = sharedVBO;
        this.sharedEBO = sharedEBO;
    }

    public int getSharedVAO() { return sharedVAO; }
    public int getSharedVBO() { return sharedVBO; }
    public int getSharedEBO() { return sharedEBO; }

    public void delete() {
        if (deleted) return;
        deleted = true;
        if (sharedVAO != 0) GLStateManager.glDeleteVertexArrays(sharedVAO);
        if (sharedVBO != 0) GLStateManager.glDeleteBuffers(sharedVBO);
        if (sharedEBO != 0) GLStateManager.glDeleteBuffers(sharedEBO);
        sharedVAO = sharedVBO = sharedEBO = 0;
    }

    @Override
    public String toString() {
        return "IndexedDrawBatch(vao=" + sharedVAO + ", vbo=" + sharedVBO + ", ebo=" + sharedEBO + ")";
    }
}
