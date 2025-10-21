package com.gtnewhorizons.angelica.client.font;

/**
 * Thread-local flags controlling how Angelica's font renderer interprets formatting codes.
 */
public final class AngelicaFontRenderContext {

    private static final ThreadLocal<Integer> RAW_TEXT_DEPTH = ThreadLocal.withInitial(() -> 0);

    private AngelicaFontRenderContext() {}

    public static void pushRawTextRendering() {
        RAW_TEXT_DEPTH.set(RAW_TEXT_DEPTH.get() + 1);
    }

    public static void popRawTextRendering() {
        int depth = RAW_TEXT_DEPTH.get() - 1;
        if (depth <= 0) {
            RAW_TEXT_DEPTH.set(0);
        } else {
            RAW_TEXT_DEPTH.set(depth);
        }
    }

    public static boolean isRawTextRendering() {
        return RAW_TEXT_DEPTH.get() > 0;
    }

}
