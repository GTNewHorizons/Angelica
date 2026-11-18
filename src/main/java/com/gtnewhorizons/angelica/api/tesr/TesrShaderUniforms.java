package com.gtnewhorizons.angelica.api.tesr;

/** Uniforms of a source-registered {@link TesrShader}; render thread, program in use, no draws */
public interface TesrShaderUniforms {

    /** After each variant link (first draw, resource reload): resolve locations, set constants */
    default void angelica$linked(int program, boolean instanced) {}

    /** Per layer bind: upload per-draw values; Angelica's own uniforms are not uploaded yet */
    void angelica$bound(int program, boolean instanced);
}
