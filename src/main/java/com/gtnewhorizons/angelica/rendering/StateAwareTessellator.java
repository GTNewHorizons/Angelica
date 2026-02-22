package com.gtnewhorizons.angelica.rendering;

public interface StateAwareTessellator {
    /**
     * True if the vertex originated from a RenderBlocks method call with the enableAO flag set.
     */
    int RENDERED_WITH_VANILLA_AO = 0x1;

    void angelica$setAppliedAo(boolean flag);

    int[] angelica$getVertexStates();
}
