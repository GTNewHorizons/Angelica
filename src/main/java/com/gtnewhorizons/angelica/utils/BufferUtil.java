package com.gtnewhorizons.angelica.utils;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCopy;

/**
 * Utility class for buffer operations in display list commands.
 */
public final class BufferUtil {
    private BufferUtil() {} // Prevent instantiation

    /**
     * Creates a copy of a FloatBuffer.
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

    /**
     * Creates a copy of a direct FloatBuffer using native memory.
     * Caller is responsible for freeing the returned buffer with memFree().
     *
     * @param src Direct FloatBuffer to copy (must be direct)
     * @return New direct FloatBuffer with copied data
     */
    public static FloatBuffer copyDirectBuffer(FloatBuffer src) {
        final int count = src.remaining();
        final FloatBuffer dst = memAllocFloat(count);
        memCopy(memAddress(src), memAddress(dst), (long) count * Float.BYTES);
        return dst;
    }
}
