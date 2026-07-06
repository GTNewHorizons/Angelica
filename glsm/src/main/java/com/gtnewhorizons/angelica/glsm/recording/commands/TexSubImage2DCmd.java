package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.PixelUnpackState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Command: glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels)
 * Updates a portion of an existing two-dimensional texture image during display list playback.
 */
public record TexSubImage2DCmd(
    int target,
    int level,
    int xoffset,
    int yoffset,
    int width,
    int height,
    int format,
    int type,
    @Nullable ByteBuffer pixels,
    @Nullable PixelUnpackState unpack
) implements DisplayListCommand {

    public TexSubImage2DCmd {
        if (pixels == null) {
            unpack = null;
        }
    }

    public static TexSubImage2DCmd fromIntBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable IntBuffer pixels, PixelUnpackState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromFloatBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable FloatBuffer pixels, PixelUnpackState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromDoubleBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable DoubleBuffer pixels, PixelUnpackState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexSubImage2DCmd fromByteBuffer(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, @Nullable ByteBuffer pixels, PixelUnpackState unpack) {
        return new TexSubImage2DCmd(target, level, xoffset, yoffset, width, height, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    @Override
    public void execute() {
        if (pixels == null) {
            GLStateManager.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, (ByteBuffer) null);
            return;
        }
        GLStateManager.forcePixelUnpackState(unpack);
        try {
            GLStateManager.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
        } finally {
            GLStateManager.restorePixelUnpackState(unpack);
        }
    }

    @Override
    public void delete() {
        if (pixels != null) {
            memFree(pixels);
        }
    }

    @Override
    public @NotNull String toString() {
        String dataStr = pixels != null ? (pixels.remaining() + " bytes") : "null";
        return String.format("TexSubImage2D(target=%s, level=%d, offset=(%d,%d), %dx%d, format=%s, type=%s, data=%s, unpack=%s)",
            GLDebug.getTextureTargetName(target), level, xoffset, yoffset, width, height,
            GLDebug.getTextureFormatName(format), GLDebug.getDataTypeName(type), dataStr, unpack);
    }
}
