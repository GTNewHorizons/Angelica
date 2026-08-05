package com.gtnewhorizons.angelica.rendering;

public interface StateAwareTessellator {

    /// True if the vertex originated from a RenderBlocks method call with the enableAO flag set.
    int RENDERED_WITH_VANILLA_AO = 0x1;
    int NO_DIRECTIONAL_SHADING = 0x2;

    void angelica$setAppliedAo(boolean flag);
    /// Used by GTNHLib, possibly others.
    @SuppressWarnings("Unused")
    void angelica$setNoDirectionalShading(boolean flag);

    /// Sets whether we're doing terrain meshing as part of celeritas -- collects additional information.
    ///
    /// Enables per-vertex AO state collection into [#angelica$getVertexStates()].
    void angelica$setCeleritasMeshing(boolean active);

    int[] angelica$getVertexStates();

    int[] angelica$getShaderOverrideBlockIds();

    void angelica$setShaderOverrideBlockId(short blockId);
}
