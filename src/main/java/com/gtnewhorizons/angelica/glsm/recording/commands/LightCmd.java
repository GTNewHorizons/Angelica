package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.BufferUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Command: glLight(light, pname, params)
 * Sets multiple float parameters for a light source.
 * Buffer data is copied to prevent issues with buffer reuse.
 */
@Desugar
public record LightCmd(int light, int pname, FloatBuffer params) implements DisplayListCommand {

    /**
     * Creates a LightCmd with a copy of the buffer.
     */
    public static LightCmd fromBuffer(int light, int pname, FloatBuffer buffer) {
        return new LightCmd(light, pname, BufferUtil.copyDirectBuffer(buffer));
    }

    /**
     * Creates a LightCmd from a float array.
     */
    public static LightCmd fromArray(int light, int pname, float[] array) {
        return new LightCmd(light, pname, FloatBuffer.wrap(array));
    }

    @Override
    public void execute() {
        GLStateManager.glLight(light, pname, params);
    }

    @Override
    public void delete() {
        memFree(params);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Light(%s, %s, params[%d])", GLDebug.getLightName(light), GLDebug.getLightPnameName(pname), params.remaining());
    }
}
