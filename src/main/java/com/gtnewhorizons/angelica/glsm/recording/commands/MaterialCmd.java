package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.utils.BufferUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;

/**
 * Command: glMaterial(face, pname, params)
 * Sets multiple float material parameters.
 */
@Desugar
public record MaterialCmd(int face, int pname, FloatBuffer params) implements DisplayListCommand {

    /**
     * Creates a MaterialCmd with a copy of the buffer.
     */
    public static MaterialCmd fromBuffer(int face, int pname, FloatBuffer buffer) {
        return new MaterialCmd(face, pname, BufferUtil.copyBuffer(buffer));
    }

    /**
     * Creates a MaterialCmd from a float array.
     */
    public static MaterialCmd fromArray(int face, int pname, float[] array) {
        return new MaterialCmd(face, pname, FloatBuffer.wrap(array));
    }

    @Override
    public void execute() {
        GLStateManager.glMaterial(face, pname, params);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Material(%s, %s, params[%d])",
            GLDebug.getFaceName(face), GLDebug.getMaterialPnameName(pname), params.remaining());
    }
}
