package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutLong;

public final class InstancedAttribs {

    public static final int LOC_ROW0 = 5;
    public static final int LOC_ROW1 = 6;
    public static final int LOC_ROW2 = 7;
    public static final int LOC_COLOR = 8;
    public static final int LOC_OVERLAY = 9;
    public static final int LOC_LIGHTMAP = 10;
    public static final int LOC_ENTITY = 11;

    public static final int OFFSET_ROWS = 0;
    public static final int OFFSET_COLOR = 48;
    public static final int OFFSET_OVERLAY = 52;
    public static final int OFFSET_ENTITY = 56;
    public static final int HEAD_SIZE = 64;
    public static final int OFFSET_LIGHTMAP = HEAD_SIZE;
    public static final int STRIDE = 72;

    public static void writeHead(long ptr, float[] colMajor, int off, int colorABGR, int overlayABGR, long entityInfo) {
        writeRows(ptr, colMajor, off);
        writeTail(ptr, colorABGR, overlayABGR, entityInfo);
    }

    private static void writeRows(long ptr, float[] colMajor, int off) {
        for (int r = 0; r < 3; r++) {
            final long row = ptr + OFFSET_ROWS + r * 16L;
            memPutFloat(row, colMajor[off + r]);
            memPutFloat(row + 4, colMajor[off + 4 + r]);
            memPutFloat(row + 8, colMajor[off + 8 + r]);
            memPutFloat(row + 12, colMajor[off + 12 + r]);
        }
    }

    public static void writeTail(long ptr, int colorABGR, int overlayABGR, long entityInfo) {
        memPutInt(ptr + OFFSET_COLOR, colorABGR);
        memPutInt(ptr + OFFSET_OVERLAY, overlayABGR);
        memPutLong(ptr + OFFSET_ENTITY, entityInfo);
    }

    public static void writeLightmap(long ptr, int packedLight) {
        memPutFloat(ptr, packedLight & 0xFFFF);
        memPutFloat(ptr + 4, (packedLight >>> 16) & 0xFFFF);
    }

    public static long packEntityInfo(int entity, int blockEntity, int item) {
        return (entity & 0xFFFFL) | ((blockEntity & 0xFFFFL) << 16) | ((item & 0xFFFFL) << 32);
    }

    public static void pointHead(long base, int stride) {
        for (int r = 0; r < 3; r++) {
            GLStateManager.glVertexAttribPointer(LOC_ROW0 + r, 4, GL11.GL_FLOAT, false, stride, base + OFFSET_ROWS + r * 16L);
        }
        GLStateManager.glVertexAttribPointer(LOC_COLOR, 4, GL11.GL_UNSIGNED_BYTE, true, stride, base + OFFSET_COLOR);
        GLStateManager.glVertexAttribPointer(LOC_OVERLAY, 4, GL11.GL_UNSIGNED_BYTE, true, stride, base + OFFSET_OVERLAY);
        GLStateManager.glVertexAttribIPointer(LOC_ENTITY, 4, GL11.GL_SHORT, stride, base + OFFSET_ENTITY);
    }

    public static void pointTemplate(long base) {
        pointHead(base, STRIDE);
        GLStateManager.glVertexAttribPointer(LOC_LIGHTMAP, 2, GL11.GL_FLOAT, false, STRIDE, base + OFFSET_LIGHTMAP);
    }

    public static void enableHeadArrays() {
        for (int loc = LOC_ROW0; loc <= LOC_ENTITY; loc++) {
            enableInstanced(loc);
        }
    }

    static void enableInstanced(int loc) {
        GLStateManager.glEnableVertexAttribArray(loc);
        GLStateManager.glVertexAttribDivisor(loc, 1);
    }

    private InstancedAttribs() {}
}
