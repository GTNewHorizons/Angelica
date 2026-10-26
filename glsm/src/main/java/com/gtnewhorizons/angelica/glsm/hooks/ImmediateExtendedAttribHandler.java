package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.ffp.InstancedAttribs;
import org.lwjgl.opengl.GL11;

public interface ImmediateExtendedAttribHandler {

    int EXT_STRIDE = 12;
    int ENTITY_IDS_STRIDE = 8;
    int LOC_MC_ENTITY = 11;
    int LOC_MID_TEX = 12;
    int LOC_TANGENT = 13;
    int LOC_MIDBLOCK = 14;
    int LOC_ENTITY_IDS = 15;

    int RAW_NORMAL_INDEX = 6;

    boolean wantsExtended();

    boolean wantsTangent();

    boolean wantsEntityIds();

    boolean wantsExtendedCapture();

    boolean wantsFaceNormals();

    void writeFaceNormals(int[] rawBuffer, int vertexCount);

    void build(int[] rawBuffer, int vertexCount, int vertsPerPrim, int normalIntIndex, long dstAddr, int dstStride);

    void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int normalOffset,
                     int vertexCount, int vertsPerPrim, long dstAddr, int dstStride);

    static int extPrimVerts(int drawMode) {
        if (drawMode == GL11.GL_QUADS) return 4;
        if (drawMode == GL11.GL_TRIANGLES) return 3;
        return 0;
    }

    static int extPrimVerts(int drawMode, int vertexCount) {
        final int p = extPrimVerts(drawMode);
        return (p != 0 && vertexCount > 0 && vertexCount % p == 0) ? p : 0;
    }

    static int texOffset(VertexFormat format) {
        int offset = 0;
        for (VertexFormatElement element : format.elementsArray) {
            if (element.getUsage() == VertexFormatElement.Usage.PRIMARY_UV) return offset;
            offset += element.getByteSize();
        }
        return -1;
    }

    static int normalOffset(VertexFormat format) {
        int offset = 0;
        for (VertexFormatElement element : format.elementsArray) {
            if (element.getUsage() == VertexFormatElement.Usage.NORMAL) return offset;
            offset += element.getByteSize();
        }
        return -1;
    }

    static void setupExtAttribPointers(long baseOffset, int stride) {
        GLSMVertexInput.glEnableVertexAttribArray(LOC_MID_TEX);
        GLSMVertexInput.glVertexAttribPointer(LOC_MID_TEX, 2, GL11.GL_FLOAT, false, stride, baseOffset);
        GLSMVertexInput.glEnableVertexAttribArray(LOC_TANGENT);
        GLSMVertexInput.glVertexAttribPointer(LOC_TANGENT, 4, GL11.GL_BYTE, true, stride, baseOffset + 8);
    }

    static void setupEntityIdsPointer(long offset, int stride) {
        GLSMVertexInput.glEnableVertexAttribArray(LOC_ENTITY_IDS);
        InstancedAttribs.pointEntityIds(LOC_ENTITY_IDS, stride, offset);
    }
}
