package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLightModelf(pname, param)
 * Sets a single float lighting model parameter.
 */
@Desugar
public record LightModelfCmd(int pname, float param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLightModelf(pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("LightModelf(%s, %.2f)", GLDebug.getLightModelPnameName(pname), param);
    }
}
