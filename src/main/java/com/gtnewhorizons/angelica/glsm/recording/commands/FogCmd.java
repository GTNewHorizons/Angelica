package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.BufferUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Command: glFog(pname, param)
 * Sets multiple float fog parameters.
 * Buffer data is copied to prevent issues with buffer reuse.
 */
@Desugar
public record FogCmd(int pname, FloatBuffer params) implements DisplayListCommand {

    /**
     * Creates a FogCmd with a defensive copy of the buffer.
     */
    public static FogCmd fromBuffer(int pname, FloatBuffer buffer) {
        return new FogCmd(pname, BufferUtil.copyDirectBuffer(buffer));
    }

    /**
     * Creates a FogCmd from a float array.
     */
    public static FogCmd fromArray(int pname, float[] array) {
        return new FogCmd(pname, FloatBuffer.wrap(array));
    }

    @Override
    public void execute() {
        GLStateManager.glFog(pname, params);
    }

    @Override
    public void delete() {
        memFree(params);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Fog(%s, params[%d])", GLDebug.getFogPnameName(pname), params.remaining());
    }
}
