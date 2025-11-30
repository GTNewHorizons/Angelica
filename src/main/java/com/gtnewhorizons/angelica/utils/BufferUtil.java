package com.gtnewhorizons.angelica.utils;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Utility class for buffer operations in display list commands.
 */
public final class BufferUtil {
    private BufferUtil() {} // Prevent instantiation

    /**
     * Creates a defensive copy of a FloatBuffer.
     * The source buffer's position is preserved.
     *
     * @param src The source buffer to copy
     * @return A new FloatBuffer containing a copy of the data
     */
    public static FloatBuffer copyBuffer(FloatBuffer src) {
        final FloatBuffer dst = BufferUtils.createFloatBuffer(src.remaining());
        final int pos = src.position();
        dst.put(src);
        dst.flip();
        src.position(pos);
        return dst;
    }
}
