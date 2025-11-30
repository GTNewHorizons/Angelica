package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLighti(light, pname, param)
 * Sets a single integer parameter for a light source.
 */
@Desugar
public record LightiCmd(int light, int pname, int param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLighti(light, pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Lighti(%s, %s, %d)", GLDebug.getLightName(light), GLDebug.getLightPnameName(pname), param);
    }
}
