package com.gtnewhorizons.angelica.glsm.utils;

public class GLHelper {

    public static float ub2f(byte b) {
        return (b & 0xFF) / 255.0F;
    }

    public static float b2f(byte b) {
        return ((b - Byte.MIN_VALUE) & 0xFF) / 255.0F;
    }

    public static float i2f(int i) { return ((i - Integer.MIN_VALUE) & 0xFFFFFF) / 4294967295.0F; }
}
