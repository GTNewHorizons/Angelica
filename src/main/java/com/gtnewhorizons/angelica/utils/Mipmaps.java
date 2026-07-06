package com.gtnewhorizons.angelica.utils;

public class Mipmaps {

    private static final float[] VALS = new float[256];

    public static float get(int i) {
        return VALS[i & 0xFF];
    }

    public static int getColorComponent(int one, int two, int three, int four, int bits) {
        final float f = Mipmaps.get(one >> bits);
        final float g = Mipmaps.get(two >> bits);
        final float h = Mipmaps.get(three >> bits);
        final float i = Mipmaps.get(four >> bits);
        final float j = (float) Math.pow((f + g + h + i) * 0.25, 0.45454545454545453);
        return (int) (j * 255.0);
    }

    static {
        for (int i = 0; i < VALS.length; ++i) {
            VALS[i] = (float) Math.pow((float) i / 255.0F, 2.2);
        }
    }
}
