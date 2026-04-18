package com.gtnewhorizons.angelica.api;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;

/**
 * Mixin interface for {@code LightDataAccess} providing per-block RGB light cache.
 *
 * <h3>Channel format</h3>
 * Individual RGB words use the {@link BlockLightProvider} packing: {@code (r << 8) | (g << 4) | b}, each channel 0-15.
 *
 */
public interface ExtLightDataAccess {

    /** RGB block light at exact world coordinates. */
    int angelica$getRGB(int x, int y, int z);

    /** RGB block light offset one block in {@code dir} (face neighbor lookup). */
    int angelica$getRGB(int x, int y, int z, ModelQuadFacing dir);

    /** RGB block light offset by two directions (edge/corner neighbor for AO). */
    int angelica$getRGB(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2);

    boolean angelica$isRGBEnabled();

    /** RGB sky light at exact world coordinates. */
    int angelica$getSkyRGB(int x, int y, int z);

    /** RGB sky light offset one block in {@code dir} (face neighbor lookup). */
    int angelica$getSkyRGB(int x, int y, int z, ModelQuadFacing dir);

    /** RGB sky light offset by two directions (edge/corner neighbor for AO). */
    int angelica$getSkyRGB(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2);

    boolean angelica$isSkyRGBEnabled();

    /**
     * Compute cache array index for the given world coordinates. Use with
     * {@link #angelica$getRGBByIndex}, {@link #angelica$getSkyRGBByIndex}, or
     * {@link #angelica$getFusedByIndex}
     */
    int angelica$getIndex(int x, int y, int z);

    /** Like {@link #angelica$getRGB(int, int, int)} but with a pre-computed cache index. */
    int angelica$getRGBByIndex(int idx, int x, int y, int z);

    /** Like {@link #angelica$getSkyRGB(int, int, int)} but with a pre-computed cache index. */
    int angelica$getSkyRGBByIndex(int idx, int x, int y, int z);

    /**
     * Single cache lookup returning both block and sky RGB in the <b>cache format</b>.
     * Upper 32 bits = block word (packed RGB | SENTINEL), lower 32 bits = sky word (packed RGB | SENTINEL).
     */
    long angelica$getFusedByIndex(int idx, int x, int y, int z);

}
