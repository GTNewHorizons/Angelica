package com.gtnewhorizons.angelica.glsm.recording.commands;

import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;

final class PixelDataSnapshot {
    private PixelDataSnapshot() {}

    static @Nullable ByteBuffer copy(@Nullable ByteBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining()).order(ByteOrder.nativeOrder());
        out.put(pixels);
        out.flip();
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable IntBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asIntBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable FloatBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 4).order(ByteOrder.nativeOrder());
        out.asFloatBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }

    static @Nullable ByteBuffer copy(@Nullable DoubleBuffer pixels) {
        if (pixels == null) return null;
        final int pos = pixels.position();
        final ByteBuffer out = memAlloc(pixels.remaining() * 8).order(ByteOrder.nativeOrder());
        out.asDoubleBuffer().put(pixels);
        pixels.position(pos);
        return out;
    }
}
