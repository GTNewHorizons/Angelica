package com.gtnewhorizons.angelica.glsm.recording.commands;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import org.jetbrains.annotations.NotNull;

/**
 * Command: glFogi(pname, param)
 * Sets a single integer fog parameter.
 */
@Desugar
public record FogiCmd(int pname, int param) implements DisplayListCommand {
    @Override
    public void execute() {
        GLStateManager.glFogi(pname, param);
    }

    @Override
    public @NotNull String toString() {
        return String.format("Fogi(%s, %d)", GLDebug.getFogPnameName(pname), param);
    }
}
