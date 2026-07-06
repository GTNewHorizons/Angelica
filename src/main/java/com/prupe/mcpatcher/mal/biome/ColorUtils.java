package com.prupe.mcpatcher.mal.biome;

import net.minecraft.util.MathHelper;

public class ColorUtils {

    public static void intToFloat3(int rgb, float[] f, int offset) {
        if ((rgb & 0xffffff) == 0xffffff) {
            f[offset] = f[offset + 1] = f[offset + 2] = 1.0f;
        } else {
            f[offset] = (float) (rgb & 0xff0000) / (float) 0xff0000;
            f[offset + 1] = (float) (rgb & 0xff00) / (float) 0xff00;
            f[offset + 2] = (float) (rgb & 0xff) / (float) 0xff;
        }
    }

    public static void intToFloat3(int rgb, float[] f) {
        intToFloat3(rgb, f, 0);
    }

    public static int float3ToInt(float[] f, int offset) {
        return ((int) (255.0f * f[offset])) << 16 | ((int) (255.0f * f[offset + 1])) << 8
            | (int) (255.0f * f[offset + 2]);
    }

    public static int float3ToInt(float[] f) {
        return float3ToInt(f, 0);
    }

    public static float clamp(float f) {
        return MathHelper.clamp_float(f, 0.0f, 1.0f);
    }
}
