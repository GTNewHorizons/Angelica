package com.gtnewhorizons.angelica.utils;

import org.embeddedt.embeddium.api.util.ColorARGB;

/**
 * Minecraft 26.2's ARGB.meanLinear and MipmapGenerator.darkenedAlphaBlend, using its 10-bit linear color tables.
 */
final class ModernMipmapBlender {
    private static final int[] SRGB_TO_LINEAR = new int[256];
    private static final float[] SRGB_TO_LINEAR_FLOAT = new float[256];
    private static final int[] LINEAR_TO_SRGB = new int[1024];

    static {
        for (int i = 0; i < SRGB_TO_LINEAR.length; i++) {
            final float channel = i / 255.0f;
            final float linear = channel >= 0.04045f ? (float) Math.pow((channel + 0.055) / 1.055, 2.4) : channel / 12.92f;
            SRGB_TO_LINEAR[i] = Math.round(linear * 1023.0f);
            SRGB_TO_LINEAR_FLOAT[i] = SRGB_TO_LINEAR[i] / 1023.0f;
        }
        for (int i = 0; i < LINEAR_TO_SRGB.length; i++) {
            final float channel = i / 1023.0f;
            final float srgb = channel >= 0.0031308f ? (float) (1.055 * Math.pow(channel, 1.0 / 2.4) - 0.055) : 12.92f * channel;
            LINEAR_TO_SRGB[i] = Math.round(srgb * 255.0f);
        }
    }

    private ModernMipmapBlender() {
    }

    static int blend(int a, int b, int c, int d, boolean darkCutout) {
        if (darkCutout) {
            return darkenedAlphaBlend(a, b, c, d);
        }
        return ColorARGB.pack(meanChannel(a, b, c, d, 16), meanChannel(a, b, c, d, 8), meanChannel(a, b, c, d, 0), (ColorARGB.unpackAlpha(a) + ColorARGB.unpackAlpha(b) + ColorARGB.unpackAlpha(c) + ColorARGB.unpackAlpha(d)) / 4);
    }

    private static int meanChannel(int a, int b, int c, int d, int shift) {
        final int linear = (SRGB_TO_LINEAR[(a >>> shift) & 255] + SRGB_TO_LINEAR[(b >>> shift) & 255] + SRGB_TO_LINEAR[(c >>> shift) & 255] + SRGB_TO_LINEAR[(d >>> shift) & 255]) / 4;
        return LINEAR_TO_SRGB[linear];
    }

    private static int darkenedAlphaBlend(int a, int b, int c, int d) {
        float alpha = 0.0f;
        float red   = 0.0f;
        float green = 0.0f;
        float blue  = 0.0f;
        if (ColorARGB.unpackAlpha(a) != 0) {
            alpha += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackAlpha(a)];
            red   += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackRed(a)];
            green += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackGreen(a)];
            blue  += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackBlue(a)];
        }
        if (ColorARGB.unpackAlpha(b) != 0) {
            alpha += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackAlpha(b)];
            red   += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackRed(b)];
            green += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackGreen(b)];
            blue  += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackBlue(b)];
        }
        if (ColorARGB.unpackAlpha(c) != 0) {
            alpha += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackAlpha(c)];
            red   += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackRed(c)];
            green += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackGreen(c)];
            blue  += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackBlue(c)];
        }
        if (ColorARGB.unpackAlpha(d) != 0) {
            alpha += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackAlpha(d)];
            red   += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackRed(d)];
            green += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackGreen(d)];
            blue  += SRGB_TO_LINEAR_FLOAT[ColorARGB.unpackBlue(d)];
        }
        return ColorARGB.pack(toSrgb(red), toSrgb(green), toSrgb(blue), toSrgb(alpha));
    }

    private static int toSrgb(float sum) {
        return LINEAR_TO_SRGB[(int) (sum * 0.25f * 1023.0f)];
    }
}
