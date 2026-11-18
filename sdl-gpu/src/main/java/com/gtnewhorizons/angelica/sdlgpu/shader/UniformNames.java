package com.gtnewhorizons.angelica.sdlgpu.shader;

public final class UniformNames {

    private UniformNames() {}

    public static int arrayIndex(String name) {
        final int len = name.length();
        if (len == 0 || name.charAt(len - 1) != ']') return -1;
        int i = len - 1;
        while (i > 0 && isDigit(name.charAt(i - 1))) i--;
        if (i == 0 || name.charAt(i - 1) != '[') return -1;
        if (name.charAt(i) == '0' && i + 1 != len - 1) return -1;
        long index = 0;
        for (int c = i; c < len - 1; c++) {
            index = index * 10 + (name.charAt(c) - '0');
            if (index > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) index;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
