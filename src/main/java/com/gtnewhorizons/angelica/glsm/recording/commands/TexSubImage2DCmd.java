package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.*;

/**
 * Command: glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels)
 * Updates a portion of an existing two-dimensional texture image during display list playback.
 */
@Desugar
public record TexSubImage2DCmd(
    int target,
    int level,
    int xoffset,
    int yoffset,
    int width,
    int height,
    int format,
    int type,
    byte @Nullable [] pixelData
) implements DisplayListCommand {

    /**
     * Create command from IntBuffer
     */
    public static TexSubImage2DCmd fromIntBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable IntBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            int pos = pixels.position();
            int limit = pixels.limit();
            int size = (limit - pos) * 4; // 4 bytes per int
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asIntBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    /**
     * Create command from FloatBuffer
     */
    public static TexSubImage2DCmd fromFloatBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable FloatBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            int pos = pixels.position();
            int limit = pixels.limit();
            int size = (limit - pos) * 4; // 4 bytes per float
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asFloatBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    /**
     * Create command from DoubleBuffer
     */
    public static TexSubImage2DCmd fromDoubleBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable DoubleBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            int pos = pixels.position();
            int limit = pixels.limit();
            int size = (limit - pos) * 8; // 8 bytes per double
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asDoubleBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    /**
     * Create command from ByteBuffer
     */
    public static TexSubImage2DCmd fromByteBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            int pos = pixels.position();
            int limit = pixels.limit();
            int size = limit - pos;
            data = new byte[size];
            pixels.get(data);
            pixels.position(pos); // Restore position
        }
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    /**
     * Create command from ShortBuffer
     */
    public static TexSubImage2DCmd fromShortBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable ShortBuffer pixels) {
        byte[] data = null;
        if (pixels != null) {
            int pos = pixels.position();
            int limit = pixels.limit();
            int size = (limit - pos) * 2; // 2 bytes per short
            data = new byte[size];
            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
            bb.asShortBuffer().put(pixels);
            pixels.position(pos); // Restore position
        }
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, data);
    }

    @Override
    public void execute() {
        ByteBuffer buffer = null;
        if (pixelData != null) {
            buffer = ByteBuffer.allocateDirect(pixelData.length).order(ByteOrder.nativeOrder());
            buffer.put(pixelData);
            buffer.flip();
        }
        GLStateManager.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, buffer);
    }

    @Override
    public @NotNull String toString() {
        String dataStr = pixelData != null ? (pixelData.length + " bytes") : "null";
        return String.format("TexSubImage2D(target=%s, level=%d, offset=(%d,%d), %dx%d, format=%s, type=%s, data=%s)",
            GLDebug.getTextureTargetName(target), level, xoffset, yoffset, width, height,
            GLDebug.getTextureFormatName(format), GLDebug.getDataTypeName(type), dataStr);
    }
}
