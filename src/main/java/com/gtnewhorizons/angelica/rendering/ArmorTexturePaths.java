package com.gtnewhorizons.angelica.rendering;

import java.util.Arrays;

public final class ArmorTexturePaths {
    private static final int STRIDE = 5;
    private static String[] table = new String[0];

    public static String path(String prefix, int renderIndex, int slot, String type) {
        if (prefix == null || (type != null && !"overlay".equals(type))) return format(prefix, slot, type);
        String[] t = table;
        final int base = renderIndex * STRIDE;
        if (base >= t.length) {
            t = Arrays.copyOf(t, base + STRIDE);
            table = t;
        }
        if (t[base] != prefix) {
            t[base + 1] = null;
            t[base + 2] = null;
            t[base + 3] = null;
            t[base + 4] = null;
            t[base] = prefix;
        }
        final int k = base + 1 + (slot == 2 ? 1 : 0) + (type == null ? 0 : 2);
        String p = t[k];
        if (p == null) {
            p = format(prefix, slot, type);
            t[k] = p;
        }
        return p;
    }

    private static String format(String prefix, int slot, String type) {
        return "textures/models/armor/" + prefix + (slot == 2 ? "_layer_2" : "_layer_1") + (type == null ? ".png" : "_" + type + ".png");
    }

    private ArmorTexturePaths() {}
}
