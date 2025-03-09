package org.embeddedt.embeddium.impl.common.util;

public class MathUtil {
    /**
     * @return True if the specified number is greater than zero and is a power of two, otherwise false
     */
    public static boolean isPowerOfTwo(int n) {
        return ((n & (n - 1)) == 0);
    }

    public static long toMib(long bytes) {
        return bytes / (1024L * 1024L); // 1 MiB = 1048576 (2^20) bytes
    }

    public static int roundToward(int value, int factor) {
        return -Math.floorDiv(-value, factor) * factor;
    }

    public static float square(float f) {
        return f * f;
    }

    public static int mojfloor(float f) {
        int truncated = (int)f;
        return f < (float)truncated ? truncated - 1 : truncated;
    }

    public static int mojfloor(double f) {
        int truncated = (int)f;
        return f < (double)truncated ? truncated - 1 : truncated;
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
