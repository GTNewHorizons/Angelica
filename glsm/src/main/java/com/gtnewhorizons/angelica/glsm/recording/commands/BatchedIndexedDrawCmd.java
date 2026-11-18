package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;

public final class BatchedIndexedDrawCmd implements DisplayListCommand {
    private IndexedDrawBatch batch;
    private int drawMode;
    private int indexCount;
    private int indexType;
    private long indexByteOffset;

    public void fill(IndexedDrawBatch batch, int drawMode, int indexCount, int indexType, long indexByteOffset) {
        this.batch = batch;
        this.drawMode = drawMode;
        this.indexCount = indexCount;
        this.indexType = indexType;
        this.indexByteOffset = indexByteOffset;
    }

    public int getDrawMode() { return drawMode; }
    public int getIndexCount() { return indexCount; }
    public int getIndexType() { return indexType; }
    public long getIndexByteOffset() { return indexByteOffset; }

    @Override
    public void execute() {
        if (batch == null) return;
        final int prevVAO = GLSMVertexInput.getBoundVAO();
        try {
            batch.bind();
            GLSMDraws.glDrawElements(drawMode, indexCount, indexType, indexByteOffset);
        } finally {
            GLSMVertexInput.glBindVertexArray(prevVAO);
        }
    }

    @Override
    public String toString() {
        return "BatchedIndexedDrawCmd(batch=" + batch + ", mode=" + drawMode + ", count=" + indexCount + ", type=" + indexType + ", offset=" + indexByteOffset + ")";
    }
}
