package com.gtnewhorizons.angelica.compat.iris;

/**
 * Interface implemented by BiomeGenBase via mixin to cache biome category.
 * This avoids recomputing the category every frame for shader uniforms.
 */
public interface BiomeCategoryCache {
    /**
     * Gets the cached biome category ordinal, or -1 if not yet computed.
     */
    int iris$getCachedCategory();

    /**
     * Sets the cached biome category ordinal.
     */
    void iris$setCachedCategory(int category);
}
