package org.lwjgl;

public final class Sys {

    private Sys() {}

    public static boolean is64Bit() {
        return !"32".equals(System.getProperty("sun.arch.data.model"));
    }
}
