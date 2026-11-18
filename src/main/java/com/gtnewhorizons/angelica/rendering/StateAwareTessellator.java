package com.gtnewhorizons.angelica.rendering;

/// Despite not in the `api` package, this interface is used by GT5uNH and GTNHLib, possibly others. Be careful what you
/// change!
@SuppressWarnings("unused")
public interface StateAwareTessellator {

    /// True if the vertex originated from a RenderBlocks method call with the enableAO flag set.
    int RENDERED_WITH_VANILLA_AO = 0x1;
    int NO_DIRECTIONAL_SHADING = 0x2;

    /// Explicitly render an unmapped material instead of inheriting the surrounding block's shader ID.
    short UNMAPPED_SHADER_BLOCK_ID = Short.MIN_VALUE;

    void angelica$setAppliedAo(boolean flag);
    void angelica$setNoDirectionalShading(boolean flag);

    /// Sets whether we're doing terrain meshing as part of celeritas -- collects additional information.
    ///
    /// Enables per-vertex AO state collection into [#angelica$getVertexStates()].
    void angelica$setCeleritasMeshing(boolean active);

    default boolean angelica$isCeleritasMeshing() {
        return false;
    }

    int[] angelica$getVertexStates();

    int[] angelica$getShaderOverrideBlockIds();

    void angelica$setShaderOverrideBlockId(short blockId);

    default short angelica$getShaderOverrideBlockId() {
        return -1;
    }
}
