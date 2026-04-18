package com.gtnewhorizons.angelica.api;

/**
 * Mixin interface for {@code AoFaceData} providing per-corner RGB light values.
 * Each array has 4 elements corresponding to the quad's corner vertices.
 */
public interface ExtAoFaceData {

    /** Per-corner red light values (block light, 0-15 range). */
    float[] angelica$getRl();

    /** Per-corner green light values (block light, 0-15 range). */
    float[] angelica$getGl();

    /** Per-corner blue light values (block light, 0-15 range). */
    float[] angelica$getBl();

    float angelica$getBlendedR(float[] w);
    float angelica$getBlendedG(float[] w);
    float angelica$getBlendedB(float[] w);

    /** Per-corner red light values (sky light, 0-15 range). */
    float[] angelica$getSkyRl();

    /** Per-corner green light values (sky light, 0-15 range). */
    float[] angelica$getSkyGl();

    /** Per-corner blue light values (sky light, 0-15 range). */
    float[] angelica$getSkyBl();

    float angelica$getBlendedSkyR(float[] w);
    float angelica$getBlendedSkyG(float[] w);
    float angelica$getBlendedSkyB(float[] w);
}
