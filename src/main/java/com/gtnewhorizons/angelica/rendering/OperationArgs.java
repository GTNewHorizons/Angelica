package com.gtnewhorizons.angelica.rendering;

public final class OperationArgs {

    private OperationArgs() {}

    public static Object boxed(Object prev, float v) {
        return prev instanceof Float f && Float.floatToRawIntBits(f) == Float.floatToRawIntBits(v) ? prev : Float.valueOf(v);
    }

    public static Object boxed(Object prev, int v) {
        return prev instanceof Integer i && i == v ? prev : Integer.valueOf(v);
    }
}
