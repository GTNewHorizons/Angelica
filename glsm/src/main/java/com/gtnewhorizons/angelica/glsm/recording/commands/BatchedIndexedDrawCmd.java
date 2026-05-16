package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.GLStateManager;

public final class BatchedIndexedDrawCmd implements DisplayListCommand {
    private int sharedVAO;
    private int drawMode;
    private int indexCount;
    private int indexType;
    private long indexByteOffset;

    public void fill(int sharedVAO, int drawMode, int indexCount, int indexType, long indexByteOffset) {
        this.sharedVAO = sharedVAO;
        this.drawMode = drawMode;
        this.indexCount = indexCount;
        this.indexType = indexType;
        this.indexByteOffset = indexByteOffset;
    }

    public int getSharedVAO() { return sharedVAO; }
    public int getDrawMode() { return drawMode; }
    public int getIndexCount() { return indexCount; }
    public int getIndexType() { return indexType; }
    public long getIndexByteOffset() { return indexByteOffset; }

    @Override
    public void execute() {
        if (sharedVAO == 0) return;
        GLStateManager.glBindVertexArray(sharedVAO);
        GLStateManager.glDrawElements(drawMode, indexCount, indexType, indexByteOffset);
    }

    @Override
    public String toString() {
        return "BatchedIndexedDrawCmd(vao=" + sharedVAO + ", mode=" + drawMode + ", count=" + indexCount + ", type=" + indexType + ", offset=" + indexByteOffset + ")";
    }
}
