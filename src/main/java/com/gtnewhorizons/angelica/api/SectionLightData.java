package com.gtnewhorizons.angelica.api;

/**
 * Provides RGB light data for a single chunk section. Implemented by external providers (e.g. Supernova)
 * and returned from {@link BlockLightProvider#prepareSectionData}.
 *
 * <h3>Transport format</h3>
 * {@link #getRGBAndSkyRGB} returns a compact <b>transport format</b>: {@code ((block & 0xFFFF) << 16) | (sky & 0xFFFF)}.
 */
public interface SectionLightData {

    /**
     * Get RGB block light at section-local coordinates.
     *
     * @param localX 0-15
     * @param localY 0-15
     * @param localZ 0-15
     * @return packed {@code (r << 8) | (g << 4) | b}, each channel 0-15, or {@code -1} if unavailable
     */
    int getRGB(int localX, int localY, int localZ);

    /**
     * Get RGB sky light at section-local coordinates.
     *
     * @param localX 0-15
     * @param localY 0-15
     * @param localZ 0-15
     * @return packed {@code (r << 8) | (g << 4) | b}, each channel 0-15, or {@code -1} if unavailable
     */
    default int getSkyRGB(int localX, int localY, int localZ) { return -1; }

    /**
     * Get both block and sky RGB in a single call (transport format). Implementations can override
     * to share index computation and apply section-level fast paths.
     *
     * @return {@code ((block & 0xFFFF) << 16) | (sky & 0xFFFF)}
     */
    default long getRGBAndSkyRGB(int localX, int localY, int localZ) {
        return ((long)(getRGB(localX, localY, localZ) & 0xFFFF) << 16) | (getSkyRGB(localX, localY, localZ) & 0xFFFF);
    }
}
