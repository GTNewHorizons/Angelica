package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;

public final class EntityIdsOnlyHandler implements ImmediateExtendedAttribHandler {

    public static ImmediateExtendedAttribHandler install() {
        final ImmediateExtendedAttribHandler previous = GLSMHooks.immediateExtendedHandler;
        GLSMHooks.immediateExtendedHandler = new EntityIdsOnlyHandler();
        GLStateManager.ctx().dirtyEntityIdsAttrib = true;
        return previous;
    }

    @Override public boolean wantsExtended() { return false; }
    @Override public boolean wantsTangent() { return false; }
    @Override public boolean wantsEntityIds() { return GLSMPrograms.getActiveProgram() != 0; }
    @Override public boolean wantsExtendedCapture() { return false; }
    @Override public boolean wantsFaceNormals() { return false; }
    @Override public void writeFaceNormals(int[] rawBuffer, int vertexCount) {}
    @Override public void build(int[] rawBuffer, int vertexCount, int vertsPerPrim, int normalIntIndex, long dstAddr, int dstStride) {}
    @Override public void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int normalOffset,
                                      int vertexCount, int vertsPerPrim, long dstAddr, int dstStride) {}
}
