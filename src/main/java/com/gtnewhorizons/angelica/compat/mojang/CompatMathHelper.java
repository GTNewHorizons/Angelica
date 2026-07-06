package com.gtnewhorizons.angelica.compat.mojang;

import net.minecraft.util.MathHelper;

import com.gtnewhorizons.angelica.compat.ModStatus;

public class CompatMathHelper {
    public static int smallestEncompassingPowerOfTwo(int value) {
        int j = value - 1;
        j |= j >> 1;
        j |= j >> 2;
        j |= j >> 4;
        j |= j >> 8;
        j |= j >> 16;
        return j + 1;
    }
    public static int roundUpToMultiple(int value, int divisor) {
        if (divisor == 0) {
            return 0;
        } else if (value == 0) {
            return divisor;
        } else {
            if (value < 0) {
                divisor *= -1;
            }

            int k = value % divisor;
            return k == 0 ? value : value + divisor - k;
        }
    }

    public static int clampHeight(int y) {
        if (ModStatus.isCubicChunksLoaded) {
            // no clamping
            return y;
        } else {
            return MathHelper.clamp_int(y, 0, 255);
        }
    }
}
