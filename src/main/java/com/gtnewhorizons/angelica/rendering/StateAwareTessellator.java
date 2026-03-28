package com.gtnewhorizons.angelica.rendering;

import net.minecraft.block.Block;

public interface StateAwareTessellator {
    /**
     * True if the vertex originated from a RenderBlocks method call with the enableAO flag set.
     */
    int RENDERED_WITH_VANILLA_AO = 0x1;

    void angelica$setAppliedAo(boolean flag);

    /**
     * Sets whether we're doing terrain meshing as part of celeritas -- collects additional information.
     *
     * Enables per-vertex AO state collection into {@link #angelica$getVertexStates()}.
     */
    void angelica$setCeleritasMeshing(boolean active);

    int[] angelica$getVertexStates();

    Object[] angelica$getShaderOverridesBlock();

    int[] angelica$getShaderOverridesMeta();

    void angelica$setShaderOverride(Block block, int meta);
}
