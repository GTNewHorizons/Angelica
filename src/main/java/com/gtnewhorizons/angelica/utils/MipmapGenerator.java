package com.gtnewhorizons.angelica.utils;

import me.jellysquid.mods.sodium.client.gui.options.named.MipmapGeneration;
import org.embeddedt.embeddium.api.util.ColorARGB;

/**
 * Mipmap downsampling for atlas sprites, replacing vanilla's {@code TextureUtil.generateMipmapData}.
 */
public final class MipmapGenerator {

    static final float ALPHA_BIAS = 0.025f;
    private static final float CUTOUT_ALPHA_REF = 0.5f;
    private static final float STRICT_CUTOUT_ALPHA_REF = 0.3f;

    private MipmapGenerator() {
    }

    private static MipmapStrategy resolve(MipmapStrategy strategy, boolean hasTransparentPixel) {
        if (strategy == null || strategy == MipmapStrategy.AUTO) {
            return hasTransparentPixel ? MipmapStrategy.CUTOUT : MipmapStrategy.MEAN;
        }
        return strategy;
    }

    private static boolean isCutoutStrategy(MipmapStrategy resolved) {
        return resolved == MipmapStrategy.CUTOUT
            || resolved == MipmapStrategy.STRICT_CUTOUT
            || resolved == MipmapStrategy.DARK_CUTOUT;
    }

    // Scan order controls which equally dark color wins.
    static void fillEmptyAreasWithDarkColor(int[] image, int columns) {
        final int rows = image.length / columns;
        int darkest = ColorARGB.pack(255, 255, 255, 255);
        int darkestSum = Integer.MAX_VALUE;

        for (int x = 0; x < columns; x++) {
            for (int y = 0; y < rows; y++) {
                final int pixel = image[x + y * columns];
                if (ColorARGB.unpackAlpha(pixel) == 0) {
                    continue;
                }
                final int sum = ColorARGB.unpackRed(pixel) + ColorARGB.unpackGreen(pixel) + ColorARGB.unpackBlue(pixel);
                if (sum < darkestSum) {
                    darkestSum = sum;
                    darkest = pixel;
                }
            }
        }

        final int fill = ColorARGB.pack(3 * ColorARGB.unpackRed(darkest) / 4,
            3 * ColorARGB.unpackGreen(darkest) / 4, 3 * ColorARGB.unpackBlue(darkest) / 4, 0);

        for (int i = 0; i < image.length; i++) {
            if (ColorARGB.unpackAlpha(image[i]) == 0) {
                image[i] = fill;
            }
        }
    }

    public static int[][] generateMipLevels(int mipLevel, int width, int[][] currentMips,
                                            MipmapStrategy strategy, boolean hasTransparentPixel, MipmapGeneration generation,
                                            float alphaCutoffBias, boolean isItem) {

        if (generation == MipmapGeneration.VANILLA_1_7_10) {
            return VanillaMipmapGenerator.generateMipLevels(mipLevel, width, currentMips);
        }
        final MipmapStrategy resolved = resolve(strategy, hasTransparentPixel);
        final boolean isCutout = isCutoutStrategy(resolved);
        final boolean darkCutout = resolved == MipmapStrategy.DARK_CUTOUT;
        final float cutoutRef = resolved == MipmapStrategy.STRICT_CUTOUT ? STRICT_CUTOUT_ALPHA_REF : CUTOUT_ALPHA_REF;
        if (generation == MipmapGeneration.MODERN_VANILLA) {
            return ModernMipmapGenerator.generateMipLevels(mipLevel, width, currentMips,
                isCutout, darkCutout, cutoutRef, alphaCutoffBias, isItem);
        }

        return CeleritasMipmapGenerator.generateMipLevels(mipLevel, width, currentMips, isCutout, darkCutout, cutoutRef);
    }
}
