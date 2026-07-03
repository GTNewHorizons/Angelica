package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.lwjgl.opengl.GL11;

public interface ImmediateExtendedAttribHandler {

    int EXT_STRIDE = 12;
    int LOC_MID_TEX = 12;
    int LOC_TANGENT = 13;

    boolean wantsExtended();

    boolean wantsExtendedCapture();

    void build(int[] rawBuffer, int vertexCount, long dstAddr, int dstStride);

    void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int vertexCount, long dstAddr, int dstStride);

    static int texOffset(VertexFormat format) {
        int offset = 0;
        for (VertexFormatElement element : format.elementsArray) {
            if (element.getUsage() == VertexFormatElement.Usage.PRIMARY_UV) return offset;
            offset += element.getByteSize();
        }
        return -1;
    }

    static void setupExtAttribPointers(long baseOffset, int stride) {
        GLStateManager.glEnableVertexAttribArray(LOC_MID_TEX);
        GLStateManager.glVertexAttribPointer(LOC_MID_TEX, 2, GL11.GL_FLOAT, false, stride, baseOffset);
        GLStateManager.glEnableVertexAttribArray(LOC_TANGENT);
        GLStateManager.glVertexAttribPointer(LOC_TANGENT, 4, GL11.GL_BYTE, true, stride, baseOffset + 8);
    }
}
