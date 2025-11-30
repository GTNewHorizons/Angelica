package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLightModeli(pname, param)
 * Sets a single integer lighting model parameter.
 */
@Desugar
public record LightModeliCmd(int pname, int param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLightModeli(pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("LightModeli(%s, %d)", GLDebug.getLightModelPnameName(pname), param);
    }
}
