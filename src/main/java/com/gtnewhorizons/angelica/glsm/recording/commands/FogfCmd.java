package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glFogf(pname, param)
 * Sets a single float fog parameter.
 */
@Desugar
public record FogfCmd(int pname, float param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glFogf(pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Fogf(%s, %.2f)", GLDebug.getFogPnameName(pname), param);
    }
}
