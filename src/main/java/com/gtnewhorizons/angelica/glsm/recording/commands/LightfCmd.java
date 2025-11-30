package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glLightf(light, pname, param)
 * Sets a single float parameter for a light source.
 */
@Desugar
public record LightfCmd(int light, int pname, float param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glLightf(light, pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Lightf(%s, %s, %.2f)", GLDebug.getLightName(light), GLDebug.getLightPnameName(pname), param);
    }
}
