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
 * Command: glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels)
 * Specifies a two-dimensional texture image during display list playback.
 */
public record TexImage2DCmd(
    int target,
    int level,
    int internalformat,
    int width,
    int height,
    int border,
    int format,
    int type,
    @Nullable ByteBuffer pixels,
    @Nullable PixelUnpackState unpack
) implements DisplayListCommand {

    public TexImage2DCmd {
        if (pixels == null) {
            unpack = null;
        }
    }

    public static TexImage2DCmd fromIntBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable IntBuffer pixels, PixelUnpackState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromFloatBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable FloatBuffer pixels, PixelUnpackState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromDoubleBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable DoubleBuffer pixels, PixelUnpackState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    public static TexImage2DCmd fromByteBuffer(int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels, PixelUnpackState unpack) {
        return new TexImage2DCmd(target, level, internalformat, width, height, border, format, type, PixelDataSnapshot.copy(pixels), unpack);
    }

    @Override
    public void execute() {
        if (pixels == null) {
            GLStateManager.glTexImage2D(target, level, internalformat, width, height, border, format, type, (ByteBuffer) null);
            return;
        }
        GLStateManager.forcePixelUnpackState(unpack);
        try {
            GLStateManager.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
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
        return String.format("TexImage2D(target=%s, level=%d, internalformat=%s, %dx%d, border=%d, format=%s, type=%s, data=%s, unpack=%s)",
            GLDebug.getTextureTargetName(target), level, GLDebug.getTextureFormatName(internalformat),
            width, height, border, GLDebug.getTextureFormatName(format), GLDebug.getDataTypeName(type), dataStr, unpack);
    }
}
