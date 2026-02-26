package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Command: glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels)
 * Specifies a two-dimensional texture image during display list playback.
 */
@Desugar
public record TexImage2DCmd(
    int target,
    int level,
    int internalformat,
    int width,
    int height,
    int border,
    int format,
    int type,
    byte @Nullable [] pixelData
) implements DisplayListCommand {

    /**
     * Create command from ShortBuffer
     */
    public static TexImage2DCmd fromShortBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ShortBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            final int pos = pixels.position();
            final int limit = pixels.limit();
            final int size = (limit - pos) * 2; // 2 bytes per short
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asShortBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, data);
    }

    /**
     * Create command from IntBuffer
     */
    public static TexImage2DCmd fromIntBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            final int pos = pixels.position();
            final int limit = pixels.limit();
            final int size = (limit - pos) * 4; // 4 bytes per int
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asIntBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, data);
    }

    /**
     * Create command from FloatBuffer
     */
    public static TexImage2DCmd fromFloatBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable FloatBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            final int pos = pixels.position();
            final int limit = pixels.limit();
            final int size = (limit - pos) * 4; // 4 bytes per float
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asFloatBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, data);
    }

    /**
     * Create command from DoubleBuffer
     */
    public static TexImage2DCmd fromDoubleBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable DoubleBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            final int pos = pixels.position();
            final int limit = pixels.limit();
            final int size = (limit - pos) * 8; // 8 bytes per double
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asDoubleBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, data);
    }

    /**
     * Create command from ByteBuffer
     */
    public static TexImage2DCmd fromByteBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            final int pos = pixels.position();
            final int limit = pixels.limit();
            final int size = limit - pos;
            data = new byte[size];
            pixels.get(data);
            pixels.position(pos); // Restore position
        }
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, data);
    }

    @Override
    public void execute() {
        ByteBuffer buffer = null;
        if (pixelData != null) {
            buffer = ByteBuffer.allocateDirect(pixelData.length).order(ByteOrder.nativeOrder());
            buffer.put(pixelData);
            buffer.flip();
        }
        GLStateManager.glTexImage2D(target, level, internalformat, width, height, border, format, type, buffer);
    }

    @Override
    public @NotNull String toString() {
        final String dataStr = pixelData != null ? (pixelData.length + " bytes") : "null";
        return String.format("TexImage2D(target=%s, level=%d, internalformat=%s, %dx%d, border=%d, format=%s, type=%s, data=%s)",
            GLDebug.getTextureTargetName(target), level, GLDebug.getTextureFormatName(internalformat),
            width, height, border, GLDebug.getTextureFormatName(format), GLDebug.getDataTypeName(type), dataStr);
    }
}
