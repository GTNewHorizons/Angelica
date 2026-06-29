package com.gtnewhorizons.angelica.glsm.hooks;

public interface ImmediateExtendedAttribHandler {

    boolean wantsExtended();

    void build(int[] rawBuffer, int vertexCount, long dstAddr);

    void buildPacked(long srcBase, int stride, int posOffset, int texOffset, int vertexCount, long dstAddr);
}
