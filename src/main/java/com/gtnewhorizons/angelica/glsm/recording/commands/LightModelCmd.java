package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.BufferUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Command: glLightModel(pname, params)
 * Sets multiple float lighting model parameters.
 */
@Desugar
public record LightModelCmd(int pname, FloatBuffer params) implements DisplayListCommand {

    /**
     * Creates a LightModelCmd with a copy of the buffer.
     */
    public static LightModelCmd fromBuffer(int pname, FloatBuffer buffer) {
        return new LightModelCmd(pname, BufferUtil.copyDirectBuffer(buffer));
    }

    /**
     * Creates a LightModelCmd from a float array.
     */
    public static LightModelCmd fromArray(int pname, float[] array) {
        return new LightModelCmd(pname, FloatBuffer.wrap(array));
    }

    @Override
    public void execute() {
        GLStateManager.glLightModel(pname, params);
    }

    @Override
    public void delete() {
        memFree(params);
    }

    @Override
    public @NotNull String toString() {
        return String.format("LightModel(%s, params[%d])", GLDebug.getLightModelPnameName(pname), params.remaining());
    }
}
