package me.jellysquid.mods.sodium.client.util;

public class MathUtil {
    /**
     * @return True if the specified number is greater than zero and is a power of two, otherwise false
     */
    public static boolean isPowerOfTwo(int n) {
        return ((n & (n - 1)) == 0);
    }

    /**
     * @param value The value to be clamped
     * @param min minimum that the value will be clamped to
     * @param max maximum that the value will be clamped to
     * @return If the value is within the rand (min -> max) then it returns value, otherwise returns
     * minimum if value is smaller than minimum and maximum if value is larger than maximum.
     */
    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        } else {
            return value > max ? max : value;
        }
    }

}
